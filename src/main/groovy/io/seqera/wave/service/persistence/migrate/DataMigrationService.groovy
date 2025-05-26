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
import redis.clients.jedis.params.SetParams
/**
 * Service to migrate data from SurrealDB to Postgres
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env=['migrate'])
@Slf4j
@Context
@CompileStatic
@MigrationOnly
class DataMigrationService {

    public static final String TABLE_NAME_BUILD = 'wave_build'
    public static final String TABLE_NAME_CONTAINER_REQUEST = 'wave_request'
    public static final String TABLE_NAME_SCAN = 'wave_scan'
    public static final String TABLE_NAME_MIRROR = 'wave_mirror'

    @Value('${wave.db.migrate.page-size:1000}')
    private int pageSize

    @Value('${wave.db.migrate.delay:5s}')
    private Duration delay

    @Value('${wave.db.migrate.initial-delay:5s}')
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

    private final AtomicBoolean buildDone = new AtomicBoolean(false)
    private final AtomicBoolean requestDone = new AtomicBoolean(false)
    private final AtomicBoolean scanDone = new AtomicBoolean(false)
    private final AtomicBoolean mirrorDone = new AtomicBoolean(false)

    private static final String LOCK_KEY = "migrate-lock/v1";
    private static final Duration LOCK_EXPIRE = Duration.ofDays(30)
    private static final String LOCK_VALUE = UUID.randomUUID().toString();

    private boolean acquired

    @PostConstruct
    void init() {
        try(Jedis jedis = pool.getResource()) {
            acquired = tryAcquireLock(jedis, LOCK_KEY, LOCK_VALUE, LOCK_EXPIRE.toMillis());
        }
        if( !acquired ) {
            log.debug "Skipping migration since lock cannot be acquired"
            return
        }

        log.info("Data migration service initialized with page size: $pageSize, delay: $delay, initial delay: $initialDelay")
        if (!environment.activeNames.contains("surrealdb") || !environment.activeNames.contains("postgres")) {
            throw new IllegalStateException("Both 'surrealdb' and 'postgres' environments must be active.")
        }

        dataMigrateCache.putIfAbsent(TABLE_NAME_BUILD, new DataMigrateEntry(TABLE_NAME_BUILD, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_CONTAINER_REQUEST, new DataMigrateEntry(TABLE_NAME_CONTAINER_REQUEST, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_SCAN, new DataMigrateEntry(TABLE_NAME_SCAN, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_MIRROR, new DataMigrateEntry(TABLE_NAME_MIRROR, 0))

        taskScheduler.scheduleWithFixedDelay(initialDelay, delay, this::migrateBuildRecords)
        taskScheduler.scheduleWithFixedDelay(initialDelay, delay, this::migrateContainerRequests)
        taskScheduler.scheduleWithFixedDelay(initialDelay, delay, this::migrateScanRecords)
        taskScheduler.scheduleWithFixedDelay(initialDelay, delay, this::migrateMirrorRecords)
    }

    @PreDestroy void destroy() {
        if( acquired ) {
            // remove the lock
            try (Jedis jedis = pool.getResource()) {
                releaseLock(jedis, LOCK_KEY, LOCK_VALUE)
            }
        }
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
        migrateRecords(TABLE_NAME_CONTAINER_REQUEST,
                (Integer offset)-> surrealService.getRequestsPaginated(pageSize, offset),
                (WaveContainerRecord request)-> {
                    final id = request.id.contains("wave_request:") ? request.id.takeAfter("wave_request:") : request.id
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
        if (done.get()) {
            log.info "All $tableName records ALREADY migrated"
            return
        }

        int offset = dataMigrateCache.get(tableName).offset
        def records = fetch.apply(offset)

        if (!records || records.isEmpty()) {
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
                log.info "Migration $tableName has been interrupted"
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
    static boolean tryAcquireLock(Jedis jedis, String key, String value, long expireMillis) {
        SetParams params = new SetParams().nx().px(expireMillis);
        String result = jedis.set(key, value, params);
        return "OK".equals(result);
    }

    static void releaseLock(Jedis jedis, String key, String value) {
        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "   return redis.call('del', KEYS[1]) " +
                        "else return 0 end";

        jedis.eval(luaScript,
                java.util.Collections.singletonList(key),
                java.util.Collections.singletonList(value));
    }
}
