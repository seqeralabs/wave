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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
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
import jakarta.inject.Singleton
/**
 * Service to migrate data from SurrealDB to Postgres
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env=['migrate'])
@Slf4j
@Singleton
@CompileStatic
class DataMigrationService {

    public static final String TABLE_NAME_BUILD = 'wave_build'
    public static final String TABLE_NAME_CONTAINER_REQUEST = 'wave_container_request'
    public static final String TABLE_NAME_SCAN = 'wave_scan'
    public static final String TABLE_NAME_MIRROR = 'wave_mirror'

    @Value('${wave.db.migrate.page-size:1000}')
    private int pageSize

    @Value('${wave.db.migrate.delay:5s}')
    private Duration delay

    @Value('${wave.db.migrate.initial-delay:5s}')
    private Duration initialDelay

    @Value('${wave.db.migrate.delay:100ms}')
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

    private final AtomicBoolean buildDone = new AtomicBoolean(false)
    private final AtomicBoolean requestDone = new AtomicBoolean(false)
    private final AtomicBoolean scanDone = new AtomicBoolean(false)
    private final AtomicBoolean mirrorDone = new AtomicBoolean(false)

    @PostConstruct
    void init() {
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

    /**
     * Migrate data from SurrealDB to Postgres
     */
    void migrateBuildRecords() {
        migrateRecords(TABLE_NAME_BUILD,
                (int offset)-> surrealService.getBuildsPaginated(pageSize, offset),
                (WaveBuildRecord it)-> postgresService.saveBuildAsync(it).join(),
                buildDone )
    }

    /**
     * Migrate container requests from SurrealDB to Postgres
     */
    void migrateContainerRequests() {
        migrateRecords(TABLE_NAME_CONTAINER_REQUEST,
                (int offset)-> surrealService.getRequestsPaginated(pageSize, offset),
                (WaveContainerRecord it)-> postgresService.saveContainerRequestAsync(it).join(),
                requestDone )
    }

    /**
     * Migrate scan records from SurrealDB to Postgres
     */
    void migrateScanRecords() {
        migrateRecords(TABLE_NAME_SCAN,
                (int offset)-> surrealService.getScansPaginated(pageSize, offset),
                (WaveScanRecord it)-> postgresService.saveScanRecordAsync(it).join(),
                scanDone )
    }

    /**
     * Migrate mirror records from SurrealDB to Postgres
     */
    void migrateMirrorRecords() {
        migrateRecords(TABLE_NAME_MIRROR,
                (int offset)-> surrealService.getMirrorsPaginated(pageSize, offset),
                (MirrorResult it)-> postgresService.saveMirrorResultAsync(it).join(),
                mirrorDone )
    }


    <T> void migrateRecords(String tableName, Function<Integer,List<T>> fetch, Consumer<T> saver, AtomicBoolean done) {
        if (done.get())
            return

        int offset = dataMigrateCache.get(tableName).offset
        def records = fetch.apply(offset)

        if (!records || records.isEmpty()) {
            log.info("All $tableName records migrated.")
            done.set(true)
            return
        }

        for (def it : records) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    log.debug "Thread is interrupted - exiting $tableName method"
                    break
                }
                saver.accept(it)
                dataMigrateCache.put(tableName, new DataMigrateEntry(tableName, ++offset))
                Thread.sleep(iterationDelay.toMillis())
            }
            catch (InterruptedException e) {
                log.info "Migration $tableName has been interrupted"
                Thread.currentThread().interrupt()
            }
            catch (Exception e) {
                log.error("Error saving $tableName record: ${e.message}", e)
            }
        }

        log.info("Migrated ${records.size()} $tableName records (offset $offset)")
    }

}
