/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.persistence.migrate

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function
import javax.annotation.PreDestroy

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.scheduling.TaskScheduler
import io.seqera.util.redis.JedisLock
import io.seqera.util.redis.JedisLockManager
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.impl.SurrealClient
import io.seqera.wave.service.persistence.impl.SurrealPersistenceService
import io.seqera.wave.service.persistence.migrate.cache.DataMigrateCache
import io.seqera.wave.service.persistence.migrate.cache.DataMigrateEntry
import io.seqera.wave.service.persistence.postgres.PostgresPersistentService
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

import static io.seqera.wave.util.DurationUtils.randomDuration
/**
 * Service to migrate data from SurrealDB to Postgres
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env='migrate')
@Slf4j
@Context
@CompileStatic
@MigrationOnly
class DataMigrationService {

    public static final String TABLE_NAME_BUILD = 'wave_build'
    public static final String TABLE_NAME_REQUEST = 'wave_request'
    public static final String TABLE_NAME_SCAN = 'wave_scan'
    public static final String TABLE_NAME_MIRROR = 'wave_mirror'

    @Value('${wave.db.migrate.page-size:100}')
    private int pageSize

    @Value('${wave.db.migrate.delay:10s}')
    private Duration delay

    @Value('${wave.db.migrate.initial-delay:30s}')
    private Duration initialDelay

    @Value('${wave.db.migrate.iteration-delay:100ms}')
    private Duration iterationDelay

    @Inject
    private SurrealPersistenceService surrealService

    @Inject
    private PostgresPersistentService postgresService

    @Inject
    private SurrealClient surrealDb

    @Inject
    private DataMigrateCache dataMigrateCache

    @Inject
    private TaskScheduler taskScheduler

    @Inject
    private Environment environment

    @Inject
    private ApplicationContext applicationContext

    @Inject
    private JedisPool pool

    private Jedis conn
    private JedisLock lock

    private final AtomicBoolean buildDone = new AtomicBoolean(false)
    private final AtomicBoolean requestDone = new AtomicBoolean(false)
    private final AtomicBoolean scanDone = new AtomicBoolean(false)
    private final AtomicBoolean mirrorDone = new AtomicBoolean(false)

    private static final String LOCK_KEY = "migrate-lock/v2"

    @PostConstruct
    void init() {
        if (!environment.activeNames.contains("surrealdb") || !environment.activeNames.contains("postgres")) {
            throw new IllegalStateException("Both 'surrealdb' and 'postgres' environments must be active.")
        }

        log.info("Data migration service initialized with page size: $pageSize, delay: $delay, initial delay: $initialDelay")
        // acquire the lock to only run one instance at time
        conn = pool.getResource()
        lock = tryAcquireLock(conn, LOCK_KEY)
        if( !lock ) {
            log.debug "Skipping migration since lock cannot be acquired"
            conn.close()
            conn = null
            return
        }
        log.info("Data migration initiated")

        dataMigrateCache.putIfAbsent(TABLE_NAME_BUILD, new DataMigrateEntry(TABLE_NAME_BUILD, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_REQUEST, new DataMigrateEntry(TABLE_NAME_REQUEST, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_SCAN, new DataMigrateEntry(TABLE_NAME_SCAN, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_MIRROR, new DataMigrateEntry(TABLE_NAME_MIRROR, 0))

        taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateBuildRecords)
        taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateContainerRequests)
        taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateScanRecords)
        taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateMirrorRecords)
    }

    @PreDestroy
    void destroy() {
        log.info "Releasing lock and closing connection"
        // remove the lock & close the connection
        lock?.release()
        conn?.close()
    }

    /**
     * Migrate data from SurrealDB to Postgres
     */
    void migrateBuildRecords() {
        migrateRecords(TABLE_NAME_BUILD,
                (Integer offset)-> surrealService.getBuildsPaginated(pageSize, offset),
                (WaveBuildRecord it)-> postgresService.saveBuild(it),
                buildDone )
    }

    /**
     * Migrate container requests from SurrealDB to Postgres
     */
    void migrateContainerRequests() {
        migrateRecords(TABLE_NAME_REQUEST,
                (Integer offset)-> {
                    log.debug "Fetching container requests with offset: $offset"
                    def results = surrealService.getRequestsPaginated(pageSize, offset)
                    log.debug "Found ${results?.size()} records"
                    return results
                },
                (WaveContainerRecord request)-> {
                    log.debug "Processing request: ${request?.id}"
                    final id = request.id.contains("wave_request:") ? request.id.takeAfter("wave_request:") : request.id
                    log.info "Migration for wave_request ${request.id}"
                    postgresService.saveContainerRequest(id, request)
                },
                requestDone )
    }

    /**
     * Migrate scan records from SurrealDB to Postgres
     */
    void migrateScanRecords() {
        migrateRecords(TABLE_NAME_SCAN,
                (Integer offset)-> surrealService.getScansPaginated(pageSize, offset),
                (WaveScanRecord it)-> postgresService.saveScanRecord(it),
                scanDone )
    }

    /**
     * Migrate mirror records from SurrealDB to Postgres
     */
    void migrateMirrorRecords() {
        migrateRecords(TABLE_NAME_MIRROR,
                (Integer offset)-> surrealService.getMirrorsPaginated(pageSize, offset),
                (MirrorResult it)-> postgresService.saveMirrorResult(it),
                mirrorDone )
    }

    <T> void migrateRecords(String tableName, Function<Integer,List<T>> fetch, Consumer<T> saver, AtomicBoolean done) {
        try {
            migrateRecords0(tableName, fetch, saver, done)
        }
        catch (InterruptedException e) {
            log.info "Migration $tableName has been interrupted (1)"
            Thread.currentThread().interrupt()
        }
        catch (Throwable t) {
            log.error("Unexpected migration error - ${t.message}", t)
        }
    }

    <T> void migrateRecords0(String tableName, Function<Integer,List<T>> fetch, Consumer<T> saver, AtomicBoolean done) {
        log.info "Initiating $tableName migration"
        if (done.get()) {
            log.info "All $tableName records ALREADY migrated"
            return
        }

        int offset = dataMigrateCache.get(tableName).offset
        def records = fetch.apply(offset)

        if (!records) {
            log.info("All $tableName records migrated.")
            done.set(true)
            return
        }

        int count=0
        for (def it : records) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    log.info "Thread is interrupted - exiting $tableName method"
                    break
                }
                saver.accept(it)
                dataMigrateCache.put(tableName, new DataMigrateEntry(tableName, ++offset))
                if( ++count % 50 == 0 )
                    log.info "Migration ${tableName}; processed ${count} records"
                Thread.sleep(iterationDelay.toMillis())
            }
            catch (InterruptedException e) {
                log.info "Migration $tableName has been interrupted (1)"
                Thread.currentThread().interrupt()
            }
            catch (DataAccessException dataAccessException) {
                if (dataAccessException.message.contains("duplicate key value violates unique constraint")) {
                    log.warn("Duplicate key error for $tableName record: ${dataAccessException.message}")
                    dataMigrateCache.put(tableName, new DataMigrateEntry(tableName, ++offset))
                } else {
                    log.error("Error saving=> $tableName record: ${dataAccessException.message}")
                }
            }
            catch (Exception e) {
                log.error("Error saving $tableName record: ${e.message}", e)
            }
        }

        log.info("Migrated ${records.size()} $tableName records (offset $offset)")
    }

    // == --- jedis lock handling
    static JedisLock tryAcquireLock(Jedis conn, String key) {
        return new JedisLockManager(conn)
                .withLockAutoExpireDuration(Duration.ofDays(30))
                .tryAcquire(key)
    }

}
