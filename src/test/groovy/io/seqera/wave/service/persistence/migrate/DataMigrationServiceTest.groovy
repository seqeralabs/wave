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

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.impl.SurrealPersistenceService
import io.seqera.wave.service.persistence.migrate.cache.DataMigrateCache
import io.seqera.wave.service.persistence.migrate.cache.DataMigrateEntry
import io.seqera.wave.service.persistence.postgres.PostgresPersistentService
import io.seqera.wave.service.persistence.impl.SurrealClient
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class DataMigrationServiceTest extends Specification {

    def surrealService = Mock(SurrealPersistenceService)
    def postgresService = Mock(PostgresPersistentService)
    def surrealClient = Mock(SurrealClient)
    def dataMigrateCache = Mock(DataMigrateCache)

    def service
    int pageSize = 1000

    void setup() {
        service = new DataMigrationService()
        service.surrealService = surrealService
        service.postgresService = postgresService
        service.surrealDb = surrealClient
        service.pageSize = pageSize
        service.dataMigrateCache = dataMigrateCache
    }

    def "should not migrate if build records are empty"() {
        given:
        surrealService.getBuildsPaginated(pageSize,0) >> []
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_BUILD) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_BUILD, 0)
        when:
        service.migrateBuildRecords()

        then:
        0 * postgresService.saveBuildAsync(_)
        and:
        0 * dataMigrateCache.put(_, _)
    }

    def "should migrate build records in batches of 100"() {
        given:
        def builds = (1..100).collect { new WaveBuildRecord(buildId: it) }
        surrealService.getBuildsPaginated(pageSize,0) >> builds
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_BUILD) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_BUILD, 0)

        when:
        service.migrateBuildRecords()

        then:
        100 * postgresService.saveBuild(_)
        and:
        1 * dataMigrateCache.put(DataMigrationService.TABLE_NAME_BUILD, new DataMigrateEntry(DataMigrationService.TABLE_NAME_BUILD, 100))
    }

    def "should catch and log exception during build migration"() {
        given:
        surrealService.getBuildsPaginated(pageSize,0) >> [[id: 1]]
        postgresService.saveBuildAsync(_) >> { throw new RuntimeException("DB error") }
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_BUILD) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_BUILD, 0)

        when:
        service.migrateBuildRecords()

        then:
        noExceptionThrown()
    }

    def "should not migrate if request records are empty"() {
        given:
        surrealService.getRequestsPaginated(pageSize,0) >> []
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST, 0)

        when:
        service.migrateContainerRequests()

        then:
        0 * postgresService.saveContainerRequest(_)
        and:
        0 * dataMigrateCache.put(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST, pageSize)
    }

    def "should migrate request records in batches"() {
        given:
        def requests = (1..101).collect { new WaveContainerRecord(
                new SubmitContainerTokenRequest(towerWorkspaceId: it),
                new ContainerRequest(requestId: it, identity: PlatformId.of(new User(id: it), Mock(SubmitContainerTokenRequest))),
                null, null, null) }
        surrealService.getRequestsPaginated(pageSize,0) >> requests
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST, 0)

        when:
        service.migrateContainerRequests()

        then:
        101 * postgresService.saveContainerRequest(_,_)
        and:
        1 * dataMigrateCache.put(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST, new DataMigrateEntry(DataMigrationService.TABLE_NAME_CONTAINER_REQUEST, 101))
    }

    def "should migrate scan records in batches"() {
        given:
        def scans = (1..99).collect { new WaveScanRecord(id: it) }
        surrealService.getScansPaginated(pageSize, 0) >> scans
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_SCAN) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_SCAN, 0)

        when:
        service.migrateScanRecords()

        then:
        99 * postgresService.saveScanRecord(_)
        and:
        1 * dataMigrateCache.put(DataMigrationService.TABLE_NAME_SCAN, new DataMigrateEntry(DataMigrationService.TABLE_NAME_SCAN, 99))
    }

    def "should migrate mirror records in batches"() {
        given:
        def mirrors = (1..15).collect { new MirrorResult() }
        surrealService.getMirrorsPaginated(pageSize, 0) >> mirrors
        dataMigrateCache.get(DataMigrationService.TABLE_NAME_MIRROR) >>
                new DataMigrateEntry(DataMigrationService.TABLE_NAME_MIRROR, 0)

        when:
        service.migrateMirrorRecords()

        then:
        15 * postgresService.saveMirrorResult(_)
        and:
        1 * dataMigrateCache.put(DataMigrationService.TABLE_NAME_MIRROR, new DataMigrateEntry(DataMigrationService.TABLE_NAME_MIRROR, 15))
    }
}
