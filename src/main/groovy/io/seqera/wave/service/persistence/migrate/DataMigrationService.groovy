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
import io.micronaut.context.annotation.Secondary
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.persistence.impl.SurrealClient
import io.seqera.wave.service.persistence.impl.SurrealPersistenceService
import io.seqera.wave.service.persistence.postgres.PostgresPersistentService
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Service to migrate data from SurrealDB to Postgres
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env=['surrealdb', 'postgresql', 'migrate'])
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

    @Value('${wave.db.migrate.page-size:1000}')
    int pageSize

    @PostConstruct
    void init() {
        migrateSurrealToPostgres()
    }
    void migrateSurrealToPostgres() {
        log.info("Starting SurrealDB to Postgres migration...")

        migrateBuildRecords()
        migrateContainerRequests()
        migrateScanRecords()
        migrateMirrorRecords()

        log.info("Migration completed.")
    }
    void migrateBuildRecords() {
        int offset = 0
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
            } while (builds.size() == pageSize)

            log.info("Completed migrating all build records.")
        } catch (Exception e) {
            log.error("Error during build record migration: ${e.message}", e)
        }
    }

    void migrateContainerRequests() {
        int offset = 0
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
                        postgresService.saveContainerRequestAsync(request)
                    } catch (Exception e) {
                        log.error("Error saving container request: ${e.message}", e)
                    }
                }

                offset += pageSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
            } while (requests.size() == pageSize)

            log.info("Completed migrating all container request records.")
        } catch (Exception e) {
            log.error("Error during container request migration: ${e.message}", e)
        }
    }

    void migrateScanRecords() {
        int offset = 0
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
            } while (scans.size() == pageSize)

            log.info("Completed migrating all scan records.")
        } catch (Exception e) {
            log.error("Error during scan record migration: ${e.message}", e)
        }
    }

    void migrateMirrorRecords() {
        int offset = 0
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
            } while (mirrors.size() == pageSize)

            log.info("Completed migrating all mirror records.")
        } catch (Exception e) {
            log.error("Error during mirror record migration: ${e.message}", e)
        }
    }


}
