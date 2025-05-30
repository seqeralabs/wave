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

import static io.seqera.wave.util.DurationUtils.*

import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.function.Consumer
import java.util.function.Function

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.runtime.server.event.ServerStartupEvent
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
import jakarta.inject.Inject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
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

    @Value('${wave.db.migrate.page-size:200}')
    private int pageSize

    @Value('${wave.db.migrate.delay:5s}')
    private Duration delay

    @Value('${wave.db.migrate.initial-delay:70s}')
    private Duration launchDelay

    @Value('${wave.db.migrate.initial-delay:10s}')
    private Duration initialDelay

    @Value('${wave.db.migrate.iteration-delay:100ms}')
    private Duration iterationDelay

    @Value ('${wave.db.migrate.requests.enabled:false}')
    private boolean requestsEnabled

    @Value ('${wave.db.migrate.scans.enabled:false}')
    private boolean scansEnabled

    @Value ('${wave.db.migrate.mirrors.enabled:false}')
    private boolean mirrorsEnabled

    @Value ('${wave.db.migrate.builds.enabled:false}')
    private boolean buildsEnabled


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

    private volatile Jedis conn

    private volatile JedisLock lock

    private static final String LOCK_KEY = "migrate-lock/v2"

    private volatile ScheduledFuture mirrorTask
    private volatile ScheduledFuture buildTask
    private volatile ScheduledFuture scanTask
    private volatile ScheduledFuture requestTask


    @EventListener
    void start(ServerStartupEvent event) {
        if (!environment.activeNames.contains("surrealdb") || !environment.activeNames.contains("postgres")) {
            throw new IllegalStateException("Both 'surrealdb' and 'postgres' environments must be active.")
        }
        // launch async to not block bootstrap
        taskScheduler.schedule(launchDelay, ()->{
            try {
                launchMigration()
            }
            catch (InterruptedException e) {
                log.info "Migration launch has been interrupted (1)"
            }
            catch (Throwable e) {
                log.info("Unexpected exception during Migration launch", e)
            }
        })
    }

    @EventListener
    void stop(ServerShutdownEvent event) {
        log.info "Releasing lock and closing connection"
        // remove the lock & close the connection
        lock?.release()
        conn?.close()
    }

    void launchMigration() {
        log.info("Data migration service initialized with page size: $pageSize, delay: $delay, initial delay: $initialDelay")
        // acquire the lock to only run one instance at time
        conn = pool.getResource()
        lock = acquireLock(conn, LOCK_KEY)
        if( !lock ) {
            log.debug "Skipping migration since lock cannot be acquired"
            conn.close()
            conn = null
            return
        }
        log.info("Data migration initiated with builds enabled: $buildsEnabled, requests enabled: $requestsEnabled, scans enabled: $scansEnabled, and mirrors enabled: $mirrorsEnabled")

        dataMigrateCache.putIfAbsent(TABLE_NAME_BUILD, new DataMigrateEntry(TABLE_NAME_BUILD, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_REQUEST, new DataMigrateEntry(TABLE_NAME_REQUEST, 0, "0"))
        dataMigrateCache.putIfAbsent(TABLE_NAME_SCAN, new DataMigrateEntry(TABLE_NAME_SCAN, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_MIRROR, new DataMigrateEntry(TABLE_NAME_MIRROR, 0))

        buildTask = buildsEnabled ? taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateBuildRecords) : null
        requestTask = requestsEnabled ? taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateRequests) :  null
        scanTask = scansEnabled ? taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateScanRecords) : null
        mirrorTask = mirrorsEnabled ? taskScheduler.scheduleWithFixedDelay(randomDuration(initialDelay, 0.5f), delay, this::migrateMirrorRecords) : null
    }

    /**
     * Migrate data from SurrealDB to Postgres
     */
    void migrateBuildRecords() {
        migrateRecords(TABLE_NAME_BUILD,
                (Integer offset)-> surrealService.getBuildsPaginated(pageSize, offset),
                (WaveBuildRecord it)-> postgresService.saveBuild(it),
                buildTask )
    }

    /**
     * Migrate container requests from SurrealDB to Postgres
     */
    void migrateRequests() {
        try {
            log.info "Initiating $TABLE_NAME_REQUEST migration"
            String lastId = dataMigrateCache.get(TABLE_NAME_REQUEST).lastId
            def records = surrealService.getRequestsPaginated(pageSize, lastId)

            if (!records) {
                log.info("All $TABLE_NAME_REQUEST records migrated.")
                requestTask.cancel(false)
                return
            }

            int count = 0
            for (def it : records) {
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        log.info "Thread is interrupted - exiting $TABLE_NAME_REQUEST method"
                        break
                    }
                    postgresService.saveContainerRequest(fixRequestId(it.id), it)
                    dataMigrateCache.put(TABLE_NAME_REQUEST, new DataMigrateEntry(TABLE_NAME_REQUEST, it.id))
                    if (++count % 50 == 0)
                        log.info "Migration $TABLE_NAME_REQUEST; processed ${count} records"
                    Thread.sleep(iterationDelay.toMillis())
                }
                catch (InterruptedException e) {
                    log.info "Migration $TABLE_NAME_REQUEST has been interrupted (3)"
                    Thread.currentThread().interrupt()
                }
                catch (DataAccessException dataAccessException) {
                    if (dataAccessException.message.contains("duplicate key value violates unique constraint")) {
                        log.warn("Duplicate key error for $TABLE_NAME_REQUEST record: ${dataAccessException.message}")
                        dataMigrateCache.put(TABLE_NAME_REQUEST, new DataMigrateEntry(TABLE_NAME_REQUEST, it.id))
                    } else {
                        log.error("Error saving=> $TABLE_NAME_REQUEST record: ${dataAccessException.message}")
                    }
                }
                catch (Exception e) {
                    log.error("Error saving $TABLE_NAME_REQUEST record: ${e.message}", e)
                }
            }

            log.info("Migrated ${records.size()} $TABLE_NAME_REQUEST records (Id ${records.last.id}.)")
        }
        catch (InterruptedException e) {
            log.info "Migration $TABLE_NAME_REQUEST has been interrupted (2)"
            Thread.currentThread().interrupt()
        }
        catch (Throwable t) {
            log.error("Unexpected migration error - ${t.message}", t)
        }
    }

    protected static String fixRequestId(String id){
        if (id?.contains("wave_request:")){
            if(id.contains("wave_request:⟨")){
                return id.takeAfter("wave_request:⟨").takeBefore("⟩")
            }
            return id.takeAfter("wave_request:")
        }
        return id
    }

    /**
     * Migrate scan records from SurrealDB to Postgres
     */
    void migrateScanRecords() {
        migrateRecords(TABLE_NAME_SCAN,
                (Integer offset)-> surrealService.getScansPaginated(pageSize, offset),
                (WaveScanRecord it)-> postgresService.saveScanRecord(it),
                scanTask )
    }

    /**
     * Migrate mirror records from SurrealDB to Postgres
     */
    void migrateMirrorRecords() {
        migrateRecords(TABLE_NAME_MIRROR,
                (Integer offset)-> surrealService.getMirrorsPaginated(pageSize, offset),
                (MirrorResult it)-> postgresService.saveMirrorResult(it),
                mirrorTask )
    }

    <T> void migrateRecords(String tableName, Function<Integer,List<T>> fetch, Consumer<T> saver, ScheduledFuture task) {
        try {
            migrateRecords0(tableName, fetch, saver, task)
        }
        catch (InterruptedException e) {
            log.info "Migration $tableName has been interrupted (2)"
            Thread.currentThread().interrupt()
        }
        catch (Throwable t) {
            log.error("Unexpected migration error - ${t.message}", t)
        }
    }

    <T> void migrateRecords0(String tableName, Function<Integer,List<T>> fetch, Consumer<T> saver, ScheduledFuture task) {
        log.info "Initiating $tableName migration"
        int offset = dataMigrateCache.get(tableName).offset
        def records = fetch.apply(offset)

        if (!records) {
            log.info("All $tableName records migrated.")
            task.cancel(false)
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
                log.info "Migration $tableName has been interrupted (3)"
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
    static JedisLock acquireLock(Jedis conn, String key, Duration timeout=Duration.ofMinutes(10)) {
        try {
            final max = timeout.toMillis()
            final begin = System.currentTimeMillis()
            while( !Thread.currentThread().isInterrupted() ) {
                if( System.currentTimeMillis()-begin > max ) {
                    log.info "Lock acquire timeout reached"
                    return null
                }
                final lock = new JedisLockManager(conn)
                        .withLockAutoExpireDuration(Duration.ofDays(30))
                        .tryAcquire(key)
                if( lock )
                    return lock
                log.info "Unable to acquire lock - await 1s before retrying"
                sleep(10_000)
            }
        }
        catch (InterruptedException e) {
            log.info "Migration acquire lock has been interrupted (4)"
            Thread.currentThread().interrupt()
            return null
        }
        catch (Throwable t) {
            log.error("Unexpected error while trying to acquire the lock - ${t.message}", t)
            return null
        }
    }

}
