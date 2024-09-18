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
import java.util.concurrent.TimeUnit

import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.inspect.ContainerInspectService
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
    ContainerMirrorService service

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
        println "Temp path: $folder"
        when:
        def creds = dockerAuthService.credentialsConfigJson(null, source, target, Mock(PlatformId))
        def request = MirrorRequest.create(
                source,
                target,
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                folder,
                creds )
        and:
        service.mirrorImage(request)
        then:
        service.awaitCompletion(target)
                .get(90, TimeUnit.SECONDS)
                .succeeded()

        cleanup:
        folder?.deleteDir()
    }

    def 'should get mirror result from persistent service' () {
        given:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/dir'),
                '{config}' )
        and:
        def result = MirrorResult.from(request)
        and:
        persistenceService.saveMirrorResult(result)
        when:
        def copy = service.getMirrorResult(request.id)
        then:
        copy == result
    }

}
