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
class DataMigrationServiceSpec extends Specification {

    def surrealService = Mock(SurrealPersistenceService)
    def postgresService = Mock(PostgresPersistentService)
    def surrealClient = Mock(SurrealClient)

    def service

    void setup() {
        service = new DataMigrationService()
        service.surrealService = surrealService
        service.postgresService = postgresService
        service.surrealDb = surrealClient
    }

    def "should not migrate if build records are empty"() {
        given:
        surrealService.getAllBuilds() >> []

        when:
        service.migrateBuildRecords()

        then:
        0 * postgresService.saveBuildAsync(_)
    }

    def "should migrate build records in batches of 100"() {
        given:
        def builds = (1..100).collect { new WaveBuildRecord(buildId: it) }
        surrealService.getAllBuilds() >> builds

        when:
        service.migrateBuildRecords()

        then:
        100 * postgresService.saveBuildAsync(_)
    }

    def "should catch and log exception during build migration"() {
        given:
        surrealService.getAllBuilds() >> [[id: 1]]
        postgresService.saveBuildAsync(_) >> { throw new RuntimeException("DB error") }

        when:
        service.migrateBuildRecords()

        then:
        noExceptionThrown() // error is logged, not rethrown
    }

    def "should not migrate if request records are empty"() {
        given:
        surrealService.getAllRequests() >> []

        when:
        service.migrateContainerRequests()

        then:
        0 * postgresService.saveContainerRequestAsync(_)
    }

    def "should migrate request records in batches"() {
        given:
        def requests = (1..101).collect { new WaveContainerRecord() }
        surrealService.getAllRequests() >> requests

        when:
        service.migrateContainerRequests()

        then:
        101 * postgresService.saveContainerRequestAsync(_)
    }

    def "should migrate scan records in batches"() {
        given:
        def scans = (1..99).collect { new WaveScanRecord(id: it) }
        surrealService.getAllScans() >> scans

        when:
        service.migrateScanRecords()

        then:
        99 * postgresService.saveScanRecordAsync(_)
    }

    def "should migrate mirror records in batches"() {
        given:
        def mirrors = (1..15).collect { new MirrorResult() }
        surrealService.getAllMirrors() >> mirrors

        when:
        service.migrateMirrorRecords()

        then:
        15 * postgresService.saveMirrorResultAsync(_)
    }

    def "init should trigger migration process"() {
        given:
        1 * surrealService.getAllBuilds() >> []
        1 * surrealService.getAllRequests() >> []
        1 * surrealService.getAllScans() >> []
        1 * surrealService.getAllMirrors() >> []

        when:
        service.init()

        then:
        noExceptionThrown()
    }
}
