/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.service.retention

import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

import io.seqera.wave.configuration.RetentionConfig
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.RecordType
import io.seqera.wave.service.scan.ContainerScanService
/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RetentionServiceImplTest extends Specification {

    private RetentionConfig config(Map opts = [:]) {
        new RetentionConfig(
                enabled: true,
                build: opts.build ?: Duration.ofDays(90),
                request: opts.request ?: Duration.ofDays(90),
                mirror: opts.mirror ?: Duration.ofDays(90),
                scan: opts.scan ?: Duration.ofDays(5),
                interval: opts.interval ?: Duration.ofHours(1),
                batchSize: opts.batchSize ?: 1000 )
    }

    private RetentionServiceImpl service(Map beans) {
        def result = new RetentionServiceImpl()
        result.@config = beans.config
        result.@persistenceService = beans.persistenceService
        result.@buildLogService = beans.buildLogService
        result.@scanService = beans.scanService
        result.@clock = beans.clock ?: Clock.systemUTC()
        return result
    }

    static class MutableClock extends Clock {
        private Instant time = Instant.parse('2026-01-01T00:00:00Z')
        @Override ZoneId getZone() { ZoneOffset.UTC }
        @Override Clock withZone(ZoneId zone) { this }
        @Override Instant instant() { time }
        void advance(Duration amount) { time = time.plus(amount) }
    }

    def 'should purge each record type on its own period'() {
        given:
        def persistence = Mock(PersistenceService)
        def svc = service(
                config: config(build: Duration.ofDays(30), scan: Duration.ofDays(5)),
                persistenceService: persistence,
                buildLogService: Mock(BuildLogService),
                scanService: Mock(ContainerScanService) )
        def now = Instant.now()

        when:
        def count = svc.purge()

        then: 'builds use the 30 day period'
        1 * persistence.findIdsOlderThan(RecordType.BUILD, { it < now.minus(Duration.ofDays(29)) }, 1000) >> ['bd-1']
        1 * persistence.deleteByIds(RecordType.BUILD, ['bd-1']) >> 1
        and: 'scans use the 5 day period, not the build one'
        1 * persistence.findIdsOlderThan(RecordType.SCAN, { it > now.minus(Duration.ofDays(6)) }, 1000) >> ['sc-1', 'sc-2']
        1 * persistence.deleteByIds(RecordType.SCAN, ['sc-1', 'sc-2']) >> 2
        and: 'a type with nothing to remove is left alone'
        1 * persistence.findIdsOlderThan(RecordType.REQUEST, _, 1000) >> []
        1 * persistence.findIdsOlderThan(RecordType.MIRROR, _, 1000) >> []
        0 * persistence.deleteByIds(RecordType.REQUEST, _)
        0 * persistence.deleteByIds(RecordType.MIRROR, _)
        and:
        count == 3
    }

    def 'should remove the artifacts before the record that points at them'() {
        given:
        def persistence = Mock(PersistenceService)
        def logService = Mock(BuildLogService)
        def scanSvc = Mock(ContainerScanService)
        def svc = service(
                config: config(),
                persistenceService: persistence,
                buildLogService: logService,
                scanService: scanSvc )

        when:
        svc.purge()

        then:
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, _) >> ['bd-1', 'bd-2']
        then: 'the log and conda lock go first'
        1 * logService.deleteArtifacts('bd-1')
        1 * logService.deleteArtifacts('bd-2')
        then:
        1 * persistence.deleteByIds(RecordType.BUILD, ['bd-1', 'bd-2']) >> 2

        and:
        1 * persistence.findIdsOlderThan(RecordType.SCAN, _, _) >> ['sc-1']
        then: 'the reports go first'
        1 * scanSvc.deleteReports('sc-1')
        then:
        1 * persistence.deleteByIds(RecordType.SCAN, ['sc-1']) >> 1
    }

    def 'should keep draining a type until it is exhausted'() {
        given: 'more aged records than fit in one batch'
        def persistence = Mock(PersistenceService)
        def svc = service(
                config: config(batchSize: 2),
                persistenceService: persistence,
                buildLogService: Mock(BuildLogService),
                scanService: Mock(ContainerScanService) )

        when:
        def count = svc.purge()

        then: 'a full batch means more may remain, so it goes round again'
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, 2) >> ['bd-1', 'bd-2']
        1 * persistence.deleteByIds(RecordType.BUILD, ['bd-1', 'bd-2']) >> 2
        then:
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, 2) >> ['bd-3', 'bd-4']
        1 * persistence.deleteByIds(RecordType.BUILD, ['bd-3', 'bd-4']) >> 2
        then: 'a short batch means the type is drained'
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, 2) >> ['bd-5']
        1 * persistence.deleteByIds(RecordType.BUILD, ['bd-5']) >> 1
        and: 'the whole backlog went in one pass, not one batch per run'
        count == 5
    }

    def 'should remove the record even when one of its artifacts cannot be deleted'() {
        given:
        def persistence = Mock(PersistenceService)
        def logService = Mock(BuildLogService)
        def svc = service(
                config: config(),
                persistenceService: persistence,
                buildLogService: logService,
                scanService: Mock(ContainerScanService) )

        when:
        def count = svc.purge()

        then:
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, _) >> ['bd-1', 'bd-2']
        and: 'one object store delete fails'
        1 * logService.deleteArtifacts('bd-1') >> { throw new RuntimeException('Access denied') }
        1 * logService.deleteArtifacts('bd-2')
        and: 'both rows still go, so one bad record cannot stall everything behind it'
        1 * persistence.deleteByIds(RecordType.BUILD, ['bd-1', 'bd-2']) >> 2
        and:
        noExceptionThrown()
        count == 2
    }

    def 'should abandon a type when every artifact delete fails'() {
        given: 'a broken object store rather than a bad record'
        def persistence = Mock(PersistenceService)
        def logService = Mock(BuildLogService)
        def svc = service(
                config: config(batchSize: 2),
                persistenceService: persistence,
                buildLogService: logService,
                scanService: Mock(ContainerScanService) )

        when:
        def count = svc.purge()

        then: 'exactly one attempt - it does not keep retrying a broken dependency to the deadline'
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, 2) >> ['bd-1', 'bd-2']
        2 * logService.deleteArtifacts(_) >> { throw new RuntimeException('Access denied') }
        and: 'the rows are left for the next pass instead of being orphaned wholesale'
        0 * persistence.deleteByIds(RecordType.BUILD, _)
        and: 'other types are still attempted'
        1 * persistence.findIdsOlderThan(RecordType.SCAN, _, 2) >> []
        and:
        count == 0
    }

    def 'should bound each batch by the configured size'() {
        given:
        def persistence = Mock(PersistenceService)
        def svc = service(
                config: config(batchSize: 50),
                persistenceService: persistence,
                buildLogService: Mock(BuildLogService),
                scanService: Mock(ContainerScanService) )

        when:
        svc.purge()

        then:
        1 * persistence.findIdsOlderThan(RecordType.BUILD, _, 50) >> []
    }

    def 'should keep the records of a type whose artifact service is disabled'() {
        given: 'a deployment with the build and scan services turned off'
        def persistence = Mock(PersistenceService)
        def svc = service(
                config: config(),
                persistenceService: persistence,
                buildLogService: null,
                scanService: null )

        when:
        def count = svc.purge()

        then: 'their rows stay, because nothing could delete the objects they point at'
        0 * persistence.findIdsOlderThan(RecordType.BUILD, _, _)
        0 * persistence.deleteByIds(RecordType.BUILD, _)
        0 * persistence.findIdsOlderThan(RecordType.SCAN, _, _)
        0 * persistence.deleteByIds(RecordType.SCAN, _)
        and: 'the types that have no artifacts are purged as usual'
        1 * persistence.findIdsOlderThan(RecordType.REQUEST, _, _) >> ['rq-1']
        1 * persistence.deleteByIds(RecordType.REQUEST, ['rq-1']) >> 1
        1 * persistence.findIdsOlderThan(RecordType.MIRROR, _, _) >> []
        and:
        noExceptionThrown()
        count == 1
    }

    def 'should give every type its own share of the pass budget'() {
        given: 'a BUILD backlog that cannot drain within the pass'
        def clock = new MutableClock()
        def persistence = Mock(PersistenceService)
        def svc = service(
                config: config(batchSize: 2),
                persistenceService: persistence,
                buildLogService: Mock(BuildLogService),
                scanService: Mock(ContainerScanService),
                clock: clock )

        when:
        def count = svc.purge()

        then: 'BUILD gets a quarter of the half-interval budget - 7m30s at one minute a batch'
        8 * persistence.findIdsOlderThan(RecordType.BUILD, _, 2) >> { clock.advance(Duration.ofMinutes(1)); ['bd-1', 'bd-2'] }
        8 * persistence.deleteByIds(RecordType.BUILD, _) >> 2
        and: 'the other three still run instead of starving behind it'
        1 * persistence.findIdsOlderThan(RecordType.REQUEST, _, 2) >> []
        1 * persistence.findIdsOlderThan(RecordType.MIRROR, _, 2) >> []
        1 * persistence.findIdsOlderThan(RecordType.SCAN, _, 2) >> []
        and:
        count == 16
    }

}
