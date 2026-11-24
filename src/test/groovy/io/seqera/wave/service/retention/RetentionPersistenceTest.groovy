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

import java.time.Instant
import java.time.temporal.ChronoUnit

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.RetentionEnabled
import io.seqera.wave.cron.RetentionCron
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.RecordType
import io.seqera.wave.service.persistence.postgres.PostgresSchemaService
import io.seqera.wave.service.persistence.postgres.data.BuildRepository
import io.seqera.wave.service.persistence.postgres.data.BuildRow
import io.seqera.wave.service.persistence.postgres.data.MirrorRepository
import io.seqera.wave.service.persistence.postgres.data.MirrorRow
import io.seqera.wave.service.persistence.postgres.data.RequestRepository
import io.seqera.wave.service.persistence.postgres.data.RequestRow
import io.seqera.wave.service.persistence.postgres.data.ScanRepository
import io.seqera.wave.service.persistence.postgres.data.ScanRow
import jakarta.inject.Inject
/**
 * Verify the age-based select and delete queries used by data retention
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest(environments = 'postgres', startApplication = true, transactional = false)
@Property(name = "datasources.default.driver-class-name", value = "org.testcontainers.jdbc.ContainerDatabaseDriver")
@Property(name = "datasources.default.url", value = "jdbc:tc:postgresql:16.14:///db")
@Property(name = "wave.retention.enabled", value = "true")
class RetentionPersistenceTest extends Specification {

    @Inject
    ApplicationContext ctx


    @Inject
    BuildRepository buildRepository

    @Inject
    RequestRepository requestRepository

    @Inject
    MirrorRepository mirrorRepository

    @Inject
    ScanRepository scanRepository

    @Inject
    PersistenceService persistenceService

    @Inject
    PostgresSchemaService schemaService

    def setup() {
        schemaService.deleteAll()
    }

    private static Instant daysAgo(int days) {
        Instant.now().minus(days, ChronoUnit.DAYS)
    }

    def 'should find and delete build records older than the given instant'() {
        given:
        buildRepository.save(new BuildRow(id: 'bd-old_1', data: '{}', createdAt: daysAgo(100)))
        buildRepository.save(new BuildRow(id: 'bd-older_1', data: '{}', createdAt: daysAgo(200)))
        buildRepository.save(new BuildRow(id: 'bd-new_1', data: '{}', createdAt: daysAgo(1)))

        when:
        def ids = persistenceService.findIdsOlderThan(RecordType.BUILD, daysAgo(90), 10)
        then: 'only the aged records are selected, oldest first'
        ids == ['bd-older_1', 'bd-old_1']

        when:
        def removed = persistenceService.deleteByIds(RecordType.BUILD, ids)
        then:
        removed == 2
        and: 'the record inside the period is untouched'
        buildRepository.findById('bd-new_1').isPresent()
        !buildRepository.findById('bd-old_1').isPresent()
        !buildRepository.findById('bd-older_1').isPresent()
    }

    def 'should bound the selection by the given limit'() {
        given:
        (1..5).each { buildRepository.save(new BuildRow(id: "bd-$it", data: '{}', createdAt: daysAgo(100 + it))) }

        when:
        def ids = persistenceService.findIdsOlderThan(RecordType.BUILD, daysAgo(90), 2)
        then: 'the oldest two are taken first'
        ids == ['bd-5', 'bd-4']
    }

    def 'should return an empty list when nothing is old enough'() {
        given:
        buildRepository.save(new BuildRow(id: 'bd-new_1', data: '{}', createdAt: daysAgo(1)))

        expect:
        persistenceService.findIdsOlderThan(RecordType.BUILD, daysAgo(90), 10) == []
    }

    def 'should apply the same queries to request, mirror and scan records'() {
        given:
        requestRepository.save(new RequestRow(id: 'rq-old', data: '{}', createdAt: daysAgo(100)))
        requestRepository.save(new RequestRow(id: 'rq-new', data: '{}', createdAt: daysAgo(1)))
        mirrorRepository.save(new MirrorRow(id: 'mr-old', data: '{}', createdAt: daysAgo(100)))
        mirrorRepository.save(new MirrorRow(id: 'mr-new', data: '{}', createdAt: daysAgo(1)))
        scanRepository.save(new ScanRow(id: 'sc-old', data: '{}', createdAt: daysAgo(10)))
        scanRepository.save(new ScanRow(id: 'sc-new', data: '{}', createdAt: daysAgo(1)))

        when: 'each type is purged on its own period'
        def requestIds = persistenceService.findIdsOlderThan(RecordType.REQUEST, daysAgo(90), 10)
        def mirrorIds = persistenceService.findIdsOlderThan(RecordType.MIRROR, daysAgo(90), 10)
        def scanIds = persistenceService.findIdsOlderThan(RecordType.SCAN, daysAgo(5), 10)
        then:
        requestIds == ['rq-old']
        mirrorIds == ['mr-old']
        scanIds == ['sc-old']

        when:
        persistenceService.deleteByIds(RecordType.REQUEST, requestIds)
        persistenceService.deleteByIds(RecordType.MIRROR, mirrorIds)
        persistenceService.deleteByIds(RecordType.SCAN, scanIds)
        then: 'no other type is affected'
        requestRepository.findById('rq-new').isPresent()
        mirrorRepository.findById('mr-new').isPresent()
        scanRepository.findById('sc-new').isPresent()
        and:
        !requestRepository.findById('rq-old').isPresent()
        !mirrorRepository.findById('mr-old').isPresent()
        !scanRepository.findById('sc-old').isPresent()
    }


    def 'should wire the retention beans when the feature is enabled'() {
        expect: 'the marker gates the cron, the service and the lease, and they all resolve'
        ctx.containsBean(RetentionEnabled)
        ctx.getBean(RetentionCron)
        ctx.getBean(RetentionService) instanceof RetentionServiceImpl
        ctx.getBean(RetentionLockStore)
    }

    def 'should take the lease once, then refuse it until released'() {
        given:
        def lock = ctx.getBean(RetentionLockStore)

        expect: 'only one replica may hold a pass'
        lock.acquire('replica-a')
        !lock.acquire('replica-b')

        when: 'a replica that does not hold it tries to release'
        lock.release('replica-b')
        then: 'the lease stays with its owner'
        !lock.acquire('replica-b')

        when:
        lock.release('replica-a')
        then:
        lock.acquire('replica-b')

        cleanup:
        lock.release('replica-b')
    }
}
