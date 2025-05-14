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

import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.impl.SurrealPersistenceService
import io.seqera.wave.service.persistence.postgres.PostgresPersistentService
import io.seqera.wave.service.persistence.impl.SurrealClient
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class DataMigrationServiceTest extends Specification {

    def surrealService = Mock(SurrealPersistenceService)
    def postgresService = Mock(PostgresPersistentService)
    def surrealClient = Mock(SurrealClient)

    def service
    int pageSize = 1000

    void setup() {
        service = new DataMigrationService()
        service.surrealService = surrealService
        service.postgresService = postgresService
        service.surrealDb = surrealClient
        service.pageSize = pageSize
    }

    def "should not migrate if build records are empty"() {
        given:
        surrealService.getBuildsPaginated(pageSize,0) >> []

        when:
        service.migrateBuildRecords()

        then:
        0 * postgresService.saveBuildAsync(_)
    }

    def "should migrate build records in batches of 100"() {
        given:
        def builds = (1..100).collect { new WaveBuildRecord(buildId: it) }
        surrealService.getBuildsPaginated(pageSize,0) >> builds

        when:
        service.migrateBuildRecords()

        then:
        100 * postgresService.saveBuildAsync(_)
    }

    def "should catch and log exception during build migration"() {
        given:
        surrealService.getBuildsPaginated(pageSize,0) >> [[id: 1]]
        postgresService.saveBuildAsync(_) >> { throw new RuntimeException("DB error") }

        when:
        service.migrateBuildRecords()

        then:
        noExceptionThrown() // error is logged, not rethrown
    }

    def "should not migrate if request records are empty"() {
        given:
        surrealService.getRequestsPaginated(pageSize,0) >> []

        when:
        service.migrateContainerRequests()

        then:
        0 * postgresService.saveContainerRequestAsync(_)
    }

    def "should migrate request records in batches"() {
        given:
        def requests = (1..101).collect { new WaveContainerRecord() }
        surrealService.getRequestsPaginated(pageSize,0) >> requests

        when:
        service.migrateContainerRequests()

        then:
        101 * postgresService.saveContainerRequestAsync(_)
    }

    def "should migrate scan records in batches"() {
        given:
        def scans = (1..99).collect { new WaveScanRecord(id: it) }
        surrealService.getScansPaginated(pageSize, 0) >> scans

        when:
        service.migrateScanRecords()

        then:
        99 * postgresService.saveScanRecordAsync(_)
    }

    def "should migrate mirror records in batches"() {
        given:
        def mirrors = (1..15).collect { new MirrorResult() }
        surrealService.getMirrorsPaginated(pageSize, 0) >> mirrors

        when:
        service.migrateMirrorRecords()

        then:
        15 * postgresService.saveMirrorResultAsync(_)
    }

    def "init should trigger migration process"() {
        given:
        1 * surrealService.getBuildsPaginated(pageSize, _) >> []
        1 * surrealService.getRequestsPaginated(pageSize, _) >> []
        1 * surrealService.getScansPaginated(pageSize, _) >> []
        1 * surrealService.getMirrorsPaginated(pageSize, _) >> []

        when:
        service.init()

        then:
        noExceptionThrown()
    }
}
