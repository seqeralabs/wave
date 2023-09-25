/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.tower.User
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ViewControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    def 'should render build page' () {
        given:
        def controller = new ViewController(serverUrl: 'http://foo.com')
        and:
        def record = new WaveBuildRecord(
                buildId: '12345',
                dockerFile: 'FROM foo',
                condaFile: 'conda::foo',
                spackFile: 'some-spack-recipe',
                targetImage: 'docker.io/some:image',
                userName: 'paolo',
                userEmail: 'paolo@seqera.io',
                userId: 100,
                requestIp: '10.20.30.40',
                startTime: Instant.now(),
                offsetId: '+02:00',
                duration: Duration.ofMinutes(1),
                exitStatus: 0,
                platform: 'linux/amd64' )
        when:
        def binding = controller.renderBuildView(record)
        then:
        binding.build_id == '12345'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_image == 'docker.io/some:image'
        binding.build_user == 'paolo (ip: 10.20.30.40)'
        binding.build_platform == 'linux/amd64'
        binding.build_exit_status == 0
        binding.build_platform == 'linux/amd64'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_spackfile == 'some-spack-recipe'
        binding.build_format == 'Docker'
    }

    def 'should render a build page' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: 'test',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        when:
        persistenceService.saveBuild(record1)
        and:
        def request = HttpRequest.GET("/view/builds/${record1.buildId}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(record1.buildId)
        and:
        response.body().contains('Container file')
        response.body().contains('FROM docker.io/test:foo')
        and:
        !response.body().contains('Conda file')
        !response.body().contains('Spack file')
    }

    def 'should render a build page with conda file' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: 'test',
                condaFile: 'conda::foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        when:
        persistenceService.saveBuild(record1)
        and:
        def request = HttpRequest.GET("/view/builds/${record1.buildId}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(record1.buildId)
        and:
        response.body().contains('Container file')
        response.body().contains('-')
        and:
        response.body().contains('Conda file')
        response.body().contains('conda::foo')
        and:
        !response.body().contains('Spack file')
    }

    def 'should render a build page with spack file' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: 'test',
                spackFile: 'foo/conda/recipe',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        when:
        persistenceService.saveBuild(record1)
        and:
        def request = HttpRequest.GET("/view/builds/${record1.buildId}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(record1.buildId)
        and:
        response.body().contains('Container file')
        response.body().contains('-')
        and:
        !response.body().contains('Conda file')
        and:
        response.body().contains('Spack file')
        response.body().contains('foo/conda/recipe')
    }

    def 'should render container view page' () {
        given:
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
        def req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'xyz',
                timestamp: Instant.now().toString() )
        and:
        def data = new ContainerRequestData(1, 100, 'hello-world', 'some docker', cfg, 'some conda')
        def user = new User()
        def wave = 'https://wave.io/some/container:latest'
        def addr = '100.200.300.400'

        and:
        def exp = Instant.now().plusSeconds(3600)
        def container = new WaveContainerRecord(req, data, wave, user, addr, exp)
        def token = '12345'

        when:
        persistenceService.saveContainerRequest(token, container)
        and:
        def request = HttpRequest.GET("/view/containers/${token}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(token)
    }
}
