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

package io.seqera.wave.service.request

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.ContainerRequestConfig
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.Workflow
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerRequestServiceImplTest extends Specification {

    @Inject
    private ContainerRequestConfig config

    @Inject
    private ContainerRequestStoreImpl requestStore

    def 'should evict container request from cache'(){
        given:
        def containerTokenService = new ContainerRequestServiceImpl( containerRequestStore: requestStore, config: config )
        def TOKEN = '123abc'
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def data = ContainerRequest.of(identity: new PlatformId(user,100), containerImage: 'hello-world')
        and:
        requestStore.put(TOKEN, data)

        when:
        def request = containerTokenService.evictRequest(TOKEN)
        then:
        request == data
        requestStore.get(TOKEN) == null
    }

    def 'computeToken should skip lifecycle tracking when the watcher is disabled'() {
        given:
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def service = new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                config: new ContainerRequestConfig(watcherEnabled: false, cacheDuration: Duration.ofHours(36)))
        and:
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def request = ContainerRequest.of(
                requestId: 'req-1',
                type: ContainerRequest.Type.Container,
                identity: new PlatformId(user, 100L, null, null, 'wf-1'),
                containerImage: 'hello-world' )

        when:
        def token = service.computeToken(request)

        then: 'the fixed cache duration is used and no refresh is scheduled'
        1 * store.put('req-1', request)
        0 * range.add(_, _)
        token.expiration
    }

    // ---- check0 (watcher renewal) ----

    private static ContainerRequestConfig watcherConfig() {
        new ContainerRequestConfig(
                watcherEnabled: true,
                accessTtl: Duration.ofMinutes(15),
                cacheMaxDuration: Duration.ofDays(2),
                refreshInterval: Duration.ofSeconds(270) )
    }

    private static ContainerRequest containerRequest(String requestId, Instant creationTime) {
        final user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        ContainerRequest.of(
                requestId: requestId,
                type: ContainerRequest.Type.Container,
                identity: new PlatformId(user, 100),
                containerImage: 'hello-world',
                creationTime: creationTime )
    }

    def 'check0 should renew the token while the workflow is active and within max lifetime'() {
        given:
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def persistence = Mock(PersistenceService)
        def record = Mock(WaveContainerRecord)
        def service = Spy(new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                persistenceService: persistence, config: watcherConfig()))
        and:
        def request = containerRequest('req-1', Instant.now())
        def entry = new ContainerRequestRange.Entry('req-1', 'wf-1', Instant.now().plus(Duration.ofMinutes(15)))

        when:
        service.check0(entry, Instant.now())

        then:
        1 * store.get('req-1') >> request
        1 * service.describeWorkflow(request) >> new Workflow(id: 'wf-1', status: Workflow.WorkflowStatus.RUNNING)
        1 * persistence.loadContainerRequest('req-1') >> record
        1 * record.withExpiration(_ as Instant) >> record
        and: 'the live token is re-granted and the next check is re-armed'
        1 * store.put('req-1', request, _ as Duration)
        1 * persistence.saveContainerRequestAsync(record)
        1 * range.add(_ as ContainerRequestRange.Entry, _ as Instant)
    }

    def 'check0 should stop tracking when the workflow is no longer active'() {
        given:
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def persistence = Mock(PersistenceService)
        def service = Spy(new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                persistenceService: persistence, config: watcherConfig()))
        and:
        def request = containerRequest('req-1', Instant.now())
        def entry = new ContainerRequestRange.Entry('req-1', 'wf-1', Instant.now().plus(Duration.ofMinutes(15)))

        when:
        service.check0(entry, Instant.now())

        then:
        1 * store.get('req-1') >> request
        1 * service.describeWorkflow(request) >> new Workflow(id: 'wf-1', status: STATUS)
        and: 'no renewal and no re-arm - the token is left to lapse'
        0 * store.put('req-1', _, _)
        0 * range.add(_, _)

        where:
        STATUS << [Workflow.WorkflowStatus.SUCCEEDED, Workflow.WorkflowStatus.FAILED, Workflow.WorkflowStatus.CANCELLED]
    }

    def 'check0 should stop tracking (fail-closed) when the workflow lookup returns nothing'() {
        given:
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def persistence = Mock(PersistenceService)
        def service = Spy(new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                persistenceService: persistence, config: watcherConfig()))
        and:
        def request = containerRequest('req-1', Instant.now())
        def entry = new ContainerRequestRange.Entry('req-1', 'wf-1', Instant.now().plus(Duration.ofMinutes(15)))

        when:
        service.check0(entry, Instant.now())

        then:
        1 * store.get('req-1') >> request
        1 * service.describeWorkflow(request) >> null
        0 * store.put('req-1', _, _)
        0 * range.add(_, _)
    }

    def 'check0 should record the refresh outcome as a prometheus counter'() {
        given:
        def registry = new SimpleMeterRegistry()
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def persistence = Mock(PersistenceService)
        def service = Spy(new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                persistenceService: persistence, config: watcherConfig(), meterRegistry: registry))
        and:
        def request = containerRequest('req-1', Instant.now())
        def entry = new ContainerRequestRange.Entry('req-1', 'wf-1', Instant.now().plus(Duration.ofMinutes(15)))

        when: 'the status cannot be confirmed - a fail-closed revocation'
        service.check0(entry, Instant.now())
        then:
        1 * store.get('req-1') >> request
        1 * service.describeWorkflow(request) >> null
        and:
        registry.counter('wave.tokens.refresh', 'result', 'unresolved').count() == 1d

        when: 'the workflow is still active - the token is renewed'
        service.check0(entry, Instant.now())
        then:
        1 * store.get('req-1') >> request
        1 * service.describeWorkflow(request) >> new Workflow(id: 'wf-1', status: Workflow.WorkflowStatus.RUNNING)
        1 * persistence.loadContainerRequest('req-1') >> Mock(WaveContainerRecord) { withExpiration(_) >> it }
        and:
        registry.counter('wave.tokens.refresh', 'result', 'renewed').count() == 1d
    }

    def 'check0 should stop tracking when the request is no longer in the store'() {
        given:
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def persistence = Mock(PersistenceService)
        def service = Spy(new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                persistenceService: persistence, config: watcherConfig()))
        and:
        def entry = new ContainerRequestRange.Entry('req-1', 'wf-1', Instant.now().plus(Duration.ofMinutes(15)))

        when:
        service.check0(entry, Instant.now())

        then:
        1 * store.get('req-1') >> null
        and: 'no Tower lookup, no renewal, no re-arm'
        0 * service.describeWorkflow(_)
        0 * store.put(_, _, _)
        0 * range.add(_, _)
    }

    def 'check0 should stop tracking when the max lifetime cap is reached'() {
        given:
        def store = Mock(ContainerRequestStore)
        def range = Mock(ContainerRequestRange)
        def persistence = Mock(PersistenceService)
        def service = Spy(new ContainerRequestServiceImpl(
                containerRequestStore: store, containerRequestRange: range,
                persistenceService: persistence, config: watcherConfig()))
        and: 'a request created longer ago than the 2d max lifetime'
        def request = containerRequest('req-1', Instant.now().minus(Duration.ofDays(3)))
        def entry = new ContainerRequestRange.Entry('req-1', 'wf-1', Instant.now().plus(Duration.ofMinutes(15)))

        when:
        service.check0(entry, Instant.now())

        then:
        1 * store.get('req-1') >> request
        1 * service.describeWorkflow(request) >> new Workflow(id: 'wf-1', status: Workflow.WorkflowStatus.RUNNING)
        and: 'even though the workflow is still running, the token is not renewed'
        0 * store.put('req-1', _, _)
        0 * range.add(_, _)
    }

}
