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

package io.seqera.wave.service.mirror

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest
class ContainerMirrorServiceTest extends Specification {

    @Inject
    ContainerMirrorServiceImpl mirrorService

    @Inject
    MirrorStateStore mirrorStateStore

    @Inject
    PersistenceService persistenceService

    @Inject
    ContainerInspectService dockerAuthService

    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should mirror a container' () {
        given:
        def source = 'docker.io/hello-world:latest'
        def target = 'docker.io/pditommaso/wave-tests'
        def folder = Files.createTempDirectory('test')

        when:
        def creds = dockerAuthService.credentialsConfigJson(null, source, target, Mock(PlatformId))
        def request = MirrorRequest.create(
                source,
                target,
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                folder,
                creds,
                null,
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )
        and:
        mirrorService.mirrorImage(request)
        then:
        mirrorService.awaitCompletion(target)
                .get(90, TimeUnit.SECONDS)
                .done()

        cleanup:
        folder?.deleteDir()
    }

    def 'should get mirror result from state store' () {
        given:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/dir'),
                '{config}',
                null,
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )
        and:
        def state = MirrorResult.of(request)
        and:
        persistenceService.saveMirrorResult(state)
        when:
        def copy = mirrorService.getMirrorResult(request.mirrorId)
        then:
        copy == state
    }

    def 'should get mirror result from persistent service' () {
        given:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/dir'),
                '{config}',
                null,
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )
        and:
        def state = MirrorEntry.of(request)
        and:
        mirrorStateStore.put('target/foo', state)
        when:
        def result = mirrorService.getMirrorResult(request.mirrorId)
        then:
        result == state.result
    }

    def 'should update mirror state on job completion' () {
        given:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/dir'),
                '{config}',
                null,
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )
        and:
        def state = MirrorEntry.of(request)
        def job = JobSpec.mirror(request.mirrorId, 'mirror-123', Instant.now(), Duration.ofMillis(1), Mock(Path))
        when:
        mirrorService.onJobCompletion(job, state, new JobState(JobState.Status.SUCCEEDED, 0, 'OK'))
        then:
        def s1 = mirrorStateStore.get(request.targetImage)
        and:
        s1.done()
        s1.result.succeeded()
        s1.result.exitCode == 0
        s1.result.logs == 'OK'
        and:
        def s2 = persistenceService.loadMirrorResult(request.mirrorId)
        and:
        s2 == s1.result
    }

    def 'should update mirror state on job exception' () {
        given:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/dir'),
                '{config}',
                null,
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )
        and:
        def state = MirrorEntry.of(request)
        def job = JobSpec.mirror(request.mirrorId, 'mirror-123', Instant.now(), Duration.ofMillis(1), Mock(Path))
        when:
        mirrorService.onJobException(job, state, new Exception('Oops something went wrong'))
        then:
        def s1 = mirrorStateStore.get(request.targetImage)
        and:
        s1.done()
        and:
        !s1.result.succeeded()
        s1.result.exitCode == null
        s1.result.logs == 'Oops something went wrong'
        and:
        def s2 = persistenceService.loadMirrorResult(request.mirrorId)
        and:
        s2 == s1.result
    }

    def 'should update mirror state on job timeout' () {
        given:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/dir'),
                '{config}',
                null,
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )
        and:
        def state = MirrorEntry.of(request)
        def job = JobSpec.mirror(request.mirrorId, 'mirror-123', Instant.now(), Duration.ofMillis(1), Mock(Path))
        when:
        mirrorService.onJobTimeout(job, state)
        then:
        def s1 = mirrorStateStore.get(request.targetImage)
        and:
        s1.done()
        and:
        !s1.result.succeeded()
        s1.result.exitCode == null
        s1.result.logs == 'Container mirror timed out'
        and:
        def s2 = persistenceService.loadMirrorResult(request.mirrorId)
        and:
        s2 == s1.result
    }

}
