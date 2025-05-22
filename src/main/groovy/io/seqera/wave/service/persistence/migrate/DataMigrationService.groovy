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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.scheduling.TaskScheduler
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

    @Value('${wave.db.migrate.page-size:1000}')
    int pageSize

    @Value('${wave.db.migrate.delay:2s}')
    Duration delay

    @Value('${wave.db.migrate.initial-delay:2s}')
    Duration initialDelay

    @Inject
    SurrealPersistenceService surrealService

    @Inject
    PostgresPersistentService postgresService

    @Inject
    SurrealClient surrealDb

    @Inject
    DataMigrateCache dataMigrateCache

    @Inject
    TaskScheduler taskScheduler

    @Inject
    Environment environment

    public static final String TABLE_NAME_BUILD = 'wave_build'
    public static final String TABLE_NAME_CONTAINER_REQUEST = 'wave_container_request'
    public static final String TABLE_NAME_SCAN = 'wave_scan'
    public static final String TABLE_NAME_MIRROR = 'wave_mirror'

    private final AtomicBoolean buildDone = new AtomicBoolean(false)
    private final AtomicBoolean requestDone = new AtomicBoolean(false)
    private final AtomicBoolean scanDone = new AtomicBoolean(false)
    private final AtomicBoolean mirrorDone = new AtomicBoolean(false)

    @PostConstruct
    void init() {
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

    /**
     * Migrate data from SurrealDB to Postgres
     */
    void migrateBuildRecords() {
        if (buildDone.get()) return

        int offset = dataMigrateCache.get(TABLE_NAME_BUILD).offset
        def builds = surrealService.getBuildsPaginated(pageSize, offset)

        if (!builds || builds.isEmpty()) {
            log.info("All build records migrated.")
            buildDone.set(true)
            return
        }

        for (def build : builds) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    log.debug "Thread is interrupted - exiting migrateBuildRecords method"
                    break
                }
                postgresService.saveBuildAsync(build).join()
                offset++
            } catch (Exception e) {
                log.error("Error saving build record: ${e.message}", e)
            }
        }
        dataMigrateCache.put(TABLE_NAME_BUILD, new DataMigrateEntry(TABLE_NAME_BUILD, offset))
        log.info("Migrated ${builds.size()} build records (offset $offset)")
    }

    /**
     * Migrate container requests from SurrealDB to Postgres
     */
    void migrateContainerRequests() {
        if (requestDone.get()) return

        int offset = dataMigrateCache.get(TABLE_NAME_CONTAINER_REQUEST).offset
        def requests = surrealService.getRequestsPaginated(pageSize, offset)

        if (!requests || requests.isEmpty()) {
            log.info("All container request records migrated.")
            requestDone.set(true)
            return
        }

        for (def request : requests) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    log.debug "Thread is interrupted - exiting migrateContainerRequests method"
                    break
                }
                def id = request.id.contains("wave_request:") ? request.id.takeAfter("wave_request:") : request.id
                postgresService.saveContainerRequestAsync(id, request)
                offset++
            } catch (Exception e) {
                log.error("Error saving container request: ${e.message}", e)
            }
        }

        dataMigrateCache.put(TABLE_NAME_CONTAINER_REQUEST, new DataMigrateEntry(TABLE_NAME_CONTAINER_REQUEST, offset))
        log.info("Migrated ${requests.size()} container request records (offset $offset)")
    }

    /**
     * Migrate scan records from SurrealDB to Postgres
     */
    void migrateScanRecords() {
        if (scanDone.get()) return

        int offset = dataMigrateCache.get(TABLE_NAME_SCAN).offset
        def scans = surrealService.getScansPaginated(pageSize, offset)

        if (!scans || scans.isEmpty()) {
            log.info("All scan records migrated.")
            scanDone.set(true)
            return
        }

        for (def scan : scans) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    log.debug "Thread is interrupted - exiting migrateScanRecords method"
                    break
                }
                postgresService.saveScanRecordAsync(scan).join()
                offset++
            } catch (Exception e) {
                log.error("Error saving scan record: ${e.message}", e)
            }
        }

        dataMigrateCache.put(TABLE_NAME_SCAN, new DataMigrateEntry(TABLE_NAME_SCAN, offset))
        log.info("Migrated ${scans.size()} scan records (offset $offset)")
    }

    /**
     * Migrate mirror records from SurrealDB to Postgres
     */
    void migrateMirrorRecords() {
        if (mirrorDone.get()) return

        int offset = dataMigrateCache.get(TABLE_NAME_MIRROR).offset
        def mirrors = surrealService.getMirrorsPaginated(pageSize, offset)

        if (!mirrors || mirrors.isEmpty()) {
            log.info("All mirror records migrated.")
            mirrorDone.set(true)
            return
        }

        for (def mirror : mirrors) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    log.debug "Thread is interrupted - exiting migrateMirrorRecords method"
                    break
                }
                postgresService.saveMirrorResultAsync(mirror).join()
                offset++
            } catch (Exception e) {
                log.error("Error saving mirror record: ${e.message}", e)
            }
        }

        dataMigrateCache.put(TABLE_NAME_MIRROR, new DataMigrateEntry(TABLE_NAME_MIRROR, offset))
        log.info("Migrated ${mirrors.size()} mirror records (offset $offset)")
    }
}
