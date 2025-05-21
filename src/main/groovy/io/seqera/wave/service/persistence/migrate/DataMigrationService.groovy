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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
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
    @Inject
    private SurrealPersistenceService surrealService

    @Inject
    private PostgresPersistentService postgresService

    @Inject
    private SurrealClient surrealDb

    @Inject
    DataMigrateCache dataMigrateCache

    public static final String TABLE_NAME_BUILD = 'wave_build'
    public static final String TABLE_NAME_CONTAINER_REQUEST = 'wave_container_request'
    public static final String TABLE_NAME_SCAN = 'wave_scan'
    public static final String TABLE_NAME_MIRROR = 'wave_mirror'

    @Value('${wave.db.migrate.page-size:1000}')
    int pageSize

    @Inject
    private Environment environment

    @PostConstruct
    void init() {
        if (!environment.activeNames.contains("surrealdb") || !environment.activeNames.contains("postgres")) {
            throw new IllegalStateException("Both 'surrealdb' and 'postgresql' environments must be active.");
        }
        migrateSurrealToPostgres()
    }
    /**
     * Migrate data from SurrealDB to Postgres
     */
    void migrateSurrealToPostgres() {
        log.info("Starting SurrealDB to Postgres migration...")
        dataMigrateCache.putIfAbsent(TABLE_NAME_BUILD, new DataMigrateEntry(TABLE_NAME_BUILD, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_CONTAINER_REQUEST, new DataMigrateEntry(TABLE_NAME_CONTAINER_REQUEST, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_SCAN, new DataMigrateEntry(TABLE_NAME_SCAN, 0))
        dataMigrateCache.putIfAbsent(TABLE_NAME_MIRROR, new DataMigrateEntry(TABLE_NAME_MIRROR, 0))

        migrateBuildRecords()
        migrateContainerRequests()
        migrateScanRecords()
        migrateMirrorRecords()

        log.info("Migration completed.")
    }
    /**
     * Migrate build records from SurrealDB to Postgres
     */
    void migrateBuildRecords() {
        int offset = dataMigrateCache.get(TABLE_NAME_BUILD).offset
        List builds
        try {
            do {
                builds = surrealService.getBuildsPaginated(pageSize, offset)
                if (!builds || builds.isEmpty()) {
                    break
                }

                log.info("Migrating batch of ${builds.size()} build records (offset $offset)")
                builds.each { build ->
                    try {
                        postgresService.saveBuildAsync(build)
                    } catch (Exception e) {
                        log.error("Error saving build record: ${e.message}", e)
                    }
                }

                offset += pageSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
                dataMigrateCache.put(TABLE_NAME_BUILD, new DataMigrateEntry(TABLE_NAME_BUILD, offset))
            } while (builds.size() == pageSize)

            log.info("Completed migrating all build records.")
        } catch (Exception e) {
            log.error("Error during build record migration: ${e.message}", e)
        }
    }

    /**
     * Migrate container requests from SurrealDB to Postgres
     */
    void migrateContainerRequests() {
        int offset = dataMigrateCache.get(TABLE_NAME_CONTAINER_REQUEST).offset
        List requests
        try {
            do {
                requests = surrealService.getRequestsPaginated(pageSize, offset)
                if (!requests || requests.isEmpty()) {
                    break
                }

                log.info("Migrating batch of ${requests.size()} container request records (offset $offset)")
                requests.each { request ->
                    try {
                        def id = request.id.contains("wave_request:") ?
                                request.id.takeAfter("wave_request:") :
                                request.id
                        postgresService.saveContainerRequestAsync(id, request)
                    } catch (Exception e) {
                        log.error("Error saving container request: ${e.message}", e)
                    }
                }

                offset += pageSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
                dataMigrateCache.put(TABLE_NAME_CONTAINER_REQUEST, new DataMigrateEntry(TABLE_NAME_CONTAINER_REQUEST, offset))
            } while (requests.size() == pageSize)

            log.info("Completed migrating all container request records.")
        } catch (Exception e) {
            log.error("Error during container request migration: ${e.message}", e)
        }
    }

    /**
     * Migrate scan records from SurrealDB to Postgres
     */
    void migrateScanRecords() {
        int offset = dataMigrateCache.get(TABLE_NAME_SCAN).offset
        List scans
        try {
            do {
                scans = surrealService.getScansPaginated(pageSize, offset)
                if (!scans || scans.isEmpty()) {
                    break
                }

                log.info("Migrating batch of ${scans.size()} scan records (offset $offset)")
                scans.each { scan ->
                    try {
                        postgresService.saveScanRecordAsync(scan)
                    } catch (Exception e) {
                        log.error("Error saving scan record: ${e.message}", e)
                    }
                }

                offset += pageSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
                dataMigrateCache.put(TABLE_NAME_SCAN, new DataMigrateEntry(TABLE_NAME_SCAN, offset))
            } while (scans.size() == pageSize)

            log.info("Completed migrating all scan records.")
        } catch (Exception e) {
            log.error("Error during scan record migration: ${e.message}", e)
        }
    }

    /**
     * Migrate mirror records from SurrealDB to Postgres
     */
    void migrateMirrorRecords() {
        int offset = dataMigrateCache.get(TABLE_NAME_MIRROR).offset
        List mirrors
        try {
            do {
                mirrors = surrealService.getMirrorsPaginated(pageSize, offset)
                if (!mirrors || mirrors.isEmpty()) {
                    break
                }

                log.info("Migrating batch of ${mirrors.size()} mirror records (offset $offset)")
                mirrors.each { mirror ->
                    try {
                        postgresService.saveMirrorResultAsync(mirror)
                    } catch (Exception e) {
                        log.error("Error saving mirror record: ${e.message}", e)
                    }
                }

                offset += pageSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
                dataMigrateCache.put(TABLE_NAME_MIRROR, new DataMigrateEntry(TABLE_NAME_MIRROR, offset))
            } while (mirrors.size() == pageSize)

            log.info("Completed migrating all mirror records.")
        } catch (Exception e) {
            log.error("Error during mirror record migration: ${e.message}", e)
        }
    }

}
