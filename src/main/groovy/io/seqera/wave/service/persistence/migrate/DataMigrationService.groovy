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
@Secondary
@Singleton
@CompileStatic
class DataMigrationService {
    @Inject
    private SurrealPersistenceService surrealService

    @Inject
    private PostgresPersistentService postgresService

    @Inject
    private SurrealClient surrealDb

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
        def builds = surrealService.getAllBuilds()
        if (!builds) {
            log.info("No build records found to migrate.")
            return
        }
        log.info("Migrating ${builds.size()} build records")
        try{
            int batchSize = 100
            int total = builds.size()
            int index = 0

            while (index < total) {
                def batch = builds.subList(index, Math.min(index + batchSize, total))
                log.info("Migrating batch of ${batch.size()} build records (index $index)")

                batch.each { postgresService.saveBuildAsync(it) }

                index += batchSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
            }

            log.info("Completed migrating all ${total} build records.")
        } catch (Exception e) {
            log.error("Error migrating build records: ${e.message}", e)
        }
    }

    void migrateContainerRequests() {
        def request = surrealService.getAllRequests()
        if (!request) {
            log.info("No request records found to migrate.")
            return
        }
        log.info("Migrating ${request.size()} request records")
        try{
            int batchSize = 100
            int total = request.size()
            int index = 0

            while (index < total) {
                def batch = request.subList(index, Math.min(index + batchSize, total))
                log.info("Migrating batch of ${batch.size()} request records (index $index)")

                batch.each { postgresService.saveContainerRequestAsync(it) }

                index += batchSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
            }

            log.info("Completed migrating all ${total} request records.")
        } catch (Exception e) {
            log.error("Error migrating request records: ${e.message}", e)
        }
    }

    void migrateScanRecords() {
        def scans = surrealService.getAllScans()
        if (!scans) {
            log.info("No scan records found to migrate.")
            return
        }
        log.info("Migrating ${scans.size()} scan records")
        try{
            int batchSize = 100
            int total = scans.size()
            int index = 0

            while (index < total) {
                def batch = scans.subList(index, Math.min(index + batchSize, total))
                log.info("Migrating batch of ${batch.size()} scan records (index $index)")

                batch.each { postgresService.saveScanRecordAsync(it) }

                index += batchSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
            }

            log.info("Completed migrating all ${total} scan records.")
        } catch (Exception e) {
            log.error("Error migrating scan records: ${e.message}", e)
        }
    }

    void migrateMirrorRecords() {
        def mirrors = surrealService.getAllMirrors()
        if (!mirrors) {
            log.info("No mirror records found to migrate.")
            return
        }
        log.info("Migrating ${mirrors.size()} mirror records")
        try{
            int batchSize = 100
            int total = mirrors.size()
            int index = 0

            while (index < total) {
                def batch = mirrors.subList(index, Math.min(index + batchSize, total))
                log.info("Migrating batch of ${batch.size()} mirror records (index $index)")

                batch.each { postgresService.saveMirrorResultAsync(it) }

                index += batchSize
                log.info("Sleeping for 2 seconds between batches...")
                sleep(2000)
            }

            log.info("Completed migrating all ${total} mirror records.")
        } catch (Exception e) {
            log.error("Error migrating mirror records: ${e.message}", e)
        }
    }

}
