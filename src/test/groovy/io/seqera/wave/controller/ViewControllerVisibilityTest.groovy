/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 * The visibility flag decides what the view pages render. The default — absent means public — is
 * what keeps every client that never sends the field working exactly as it does today.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Property(name = 'wave.server.url', value = 'http://foo.com')
@MicronautTest
class ViewControllerVisibilityTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    private WaveBuildRecord buildWith(String id, Boolean isPublic) {
        return new WaveBuildRecord(
                buildId: id,
                dockerFile: 'FROM alpine',
                targetImage: 'docker.io/my/target:latest',
                userName: 'test',
                userEmail: 'test@seqera.io',
                userId: 1L,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: java.time.Duration.ofSeconds(1),
                exitStatus: 0,
                platform: 'linux/amd64',
                format: BuildFormat.DOCKER,
                isPublic: isPublic )
    }

    @Unroll
    def 'should render a build with isPublic=#flag'() {
        given:
        persistenceService.saveBuildAsync(buildWith(id, flag)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET("/view/builds/$id"), String)

        then:
        resp.status == HttpStatus.OK
        resp.body().contains(id)

        where:
        id          | flag
        'bd-pubone' | null   // absent means public - the compatibility guarantee
        'bd-pubtwo' | true
    }

    def 'should withhold a build marked private'() {
        given:
        persistenceService.saveBuildAsync(buildWith('bd-privone', false)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/builds/bd-privone'), String)

        then: 'answers 200 - the record exists and nobody is being refused'
        resp.status == HttpStatus.OK

        and: 'it points at the Containers section of Platform and leaks none of the record'
        resp.body().contains('Sign in to Seqera Platform')
        resp.body().contains('Containers section')
        !resp.body().contains('bd-privone')
        !resp.body().contains('FROM alpine')

        and: 'the record renders in its own template, not a page of its own'
        resp.body().contains('Container Build Report')
    }

    def 'should count a withheld record'() {
        given: 'a registry that records, rather than the composite the app context holds'
        def registry = new SimpleMeterRegistry()
        def controller = new ViewController(meterRegistry: registry)

        when:
        controller.privateRecordView('build-view', "build 'bd-1'")

        then: 'a 200 never shows up in error rates, so the withholding is counted'
        registry.counter('wave.view.private.records').count() == 1d
    }

    private WaveContainerRecord containerWith(String id, Boolean isPublic) {
        final req = new SubmitContainerTokenRequest(containerImage: 'docker.io/my/src:latest', isPublic: isPublic)
        final data = ContainerRequest.of(requestId: id, identity: PlatformId.NULL, containerImage: 'docker.io/my/src:latest')
        return new WaveContainerRecord(req, data, 'wave.io/my/wave:latest', '127.0.0.1', Instant.now().plusSeconds(3600))
    }

    // The container and mirror routes carried a method-level @View, which pins the template for
    // every return path and silently ignores a returned ModelAndView naming a different one. That
    // rendered the normal page with an empty model instead of the message, so this covers the
    // route shape as well as the rule.
    def 'should withhold a container request marked private'() {
        given:
        persistenceService.saveContainerRequestAsync(containerWith('cr-private', false)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/containers/cr-private'), String)

        then:
        resp.status == HttpStatus.OK
        resp.body().contains('Sign in to Seqera Platform')
        !resp.body().contains('docker.io/my/src:latest')
    }

    def 'should render a container request with no visibility flag'() {
        given:
        persistenceService.saveContainerRequestAsync(containerWith('cr-public', null)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/containers/cr-public'), String)

        then:
        resp.status == HttpStatus.OK
        !resp.body().contains('Sign in to Seqera Platform')
        resp.body().contains('docker.io/my/src:latest')
    }

    // ---- the remaining routes named in the acceptance criteria

    private MirrorResult mirrorWith(String id, String target, Boolean isPublic) {
        return new MirrorResult(
                id, 'sha256:123', 'docker.io/my/src:latest', target,
                ContainerPlatform.of('linux/amd64'), Instant.now(), 'Z', 'test', 'test@seqera.io', 1L,
                null, MirrorResult.Status.COMPLETED, java.time.Duration.ofSeconds(1), 0, 'log', isPublic)
    }

    def 'should withhold a mirror marked private'() {
        given:
        persistenceService.saveMirrorResultAsync(mirrorWith('mr-private', 'docker.io/my/tgt:latest', false)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/mirrors/mr-private'), String)

        then:
        resp.status == HttpStatus.OK
        resp.body().contains('Sign in to Seqera Platform')
        !resp.body().contains('docker.io/my/tgt:latest')
    }

    def 'should render a mirror with no visibility flag'() {
        given:
        persistenceService.saveMirrorResultAsync(mirrorWith('mr-public', 'docker.io/my/pub:latest', null)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/mirrors/mr-public'), String)

        then:
        resp.status == HttpStatus.OK
        !resp.body().contains('Sign in to Seqera Platform')
        resp.body().contains('docker.io/my/pub:latest')
    }

    def 'should withhold a scan whose build is private'() {
        given: 'a scan inherits from the build it derives from'
        persistenceService.saveBuildAsync(buildWith('bd-scanpriv', false)).join()
        persistenceService.saveScanRecordAsync(new WaveScanRecord(
                id: 'sc-private', buildId: 'bd-scanpriv', containerImage: 'docker.io/my/scanned:latest',
                startTime: Instant.now(), duration: java.time.Duration.ofSeconds(1),
                status: 'SUCCEEDED', vulnerabilities: [])).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/scans/sc-private'), String)

        then:
        resp.status == HttpStatus.OK
        resp.body().contains('Sign in to Seqera Platform')
        !resp.body().contains('docker.io/my/scanned:latest')
    }

    // ---- list pages must not leak private entries

    def 'should withhold a build list whose every match is private'() {
        given: 'two builds sharing a container id, both private'
        persistenceService.saveBuildAsync(buildWith('bd-allprivate01234_1', false)).join()
        persistenceService.saveBuildAsync(buildWith('bd-allprivate01234_2', false)).join()

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/builds/allprivate01234'), String)

        then: 'withheld, not reported as an unknown id - the matches do exist'
        resp.status == HttpStatus.OK
        resp.body().contains('Sign in to Seqera Platform')
        !resp.body().contains('No Build Record Found')

        and:
        !resp.body().contains('bd-allprivate01234_1')
        !resp.body().contains('bd-allprivate01234_2')
    }

    def 'should still report an unknown build id as unknown'() {
        when: 'nothing matches at all'
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/builds/nosuchid01234567'), String)

        then:
        resp.status == HttpStatus.OK
        !resp.body().contains('Sign in to Seqera Platform')
        resp.body().contains("Unknown build id")
    }

    // Three builds, not two: with only one survivor the route takes the single-match redirect and
    // the list is never rendered, so the filter would go untested.
    def 'should leave private builds out of the build list'() {
        given: 'three builds sharing a container id, one of them private'
        persistenceService.saveBuildAsync(buildWith('bd-listaa01234567_1', null)).join()
        persistenceService.saveBuildAsync(buildWith('bd-listaa01234567_2', false)).join()
        persistenceService.saveBuildAsync(buildWith('bd-listaa01234567_3', true)).join()

        when: 'the id with no _nn suffix renders the multi-match list'
        def resp = client.toBlocking().exchange(HttpRequest.GET('/view/builds/listaa01234567'), String)

        then: 'two survive the filter, so the list renders rather than redirecting'
        resp.status == HttpStatus.OK
        resp.body().contains('Wave Container Builds History')

        and:
        resp.body().contains('bd-listaa01234567_1')
        resp.body().contains('bd-listaa01234567_3')
        !resp.body().contains('bd-listaa01234567_2')
    }
}
