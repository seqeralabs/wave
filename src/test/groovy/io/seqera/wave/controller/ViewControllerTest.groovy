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

package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.logs.BuildLogServiceImpl
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import static io.seqera.wave.util.DataTimeUtils.formatDuration
import static io.seqera.wave.util.DataTimeUtils.formatTimestamp
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ViewControllerTest extends Specification {

    @MockBean(BuildLogServiceImpl)
    BuildLogService logsService() {
        Mock(BuildLogService)
    }

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    @Inject
    BuildLogService buildLogService

    @Inject
    private ContainerInspectService inspectService

    def 'should create build binding' () {
        given:
        def controller = new ViewController(serverUrl: 'http://foo.com', buildLogService: buildLogService)
        and:
        def record = new WaveBuildRecord(
                buildId: '12345',
                dockerFile: 'FROM foo',
                condaFile: 'conda::foo',
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
        1 * buildLogService.fetchLogString('12345') >> new BuildLogService.BuildLog('log content', false)
        and:
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
        binding.build_format == 'Docker'
        binding.build_log_data == 'log content'
        binding.build_log_truncated == false
        binding.build_log_url == 'http://foo.com/v1alpha1/builds/12345/logs'
        binding.build_success == true
        binding.build_in_progress == false
        binding.build_failed == false
    }

    def 'should render build page' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: '112233',
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
    }

    def 'should render build page with conda file' () {
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
        def user = new User(id:1)
        def identity = new PlatformId(user,100)
        and:
        def token = '12345'
        def data = ContainerRequest.of(requestId: token, identity: identity, containerImage: 'hello-world', containerFile: 'some docker', containerConfig: cfg, condaFile: 'some conda')
        def wave = 'https://wave.io/some/container:latest'
        def addr = '100.200.300.400'

        and:
        def exp = Instant.now().plusSeconds(3600)
        def container = new WaveContainerRecord(req, data, wave, addr, exp)

        when:
        persistenceService.saveContainerRequest(container)
        and:
        def request = HttpRequest.GET("/view/containers/${token}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(token)
    }

    def 'should render inspect view'() {
        when:
        def request = HttpRequest.GET('/view/inspect?image=ubuntu')
        def response = client.toBlocking().exchange(request, String)

        then:
        response.status == HttpStatus.OK
        response.body().contains('ubuntu')
        response.body().contains('latest')
        response.body().contains('https://registry-1.docker.io')
        response.body().contains('amd64')
    }

    def 'should render inspect view with platform'() {
        when:
        def request = HttpRequest.GET('/view/inspect?image=ubuntu&platform=linux/arm64')
        def response = client.toBlocking().exchange(request, String)

        then:
        response.status == HttpStatus.OK
        response.body().contains('ubuntu')
        response.body().contains('latest')
        response.body().contains('https://registry-1.docker.io')
        response.body().contains('arm64')
    }

    def 'should render in progress build page' () {
        given:
        def controller = new ViewController(serverUrl: 'http://foo.com', buildLogService: buildLogService)
        and:
        def record = new WaveBuildRecord(
                buildId: '12345',
                dockerFile: 'FROM foo',
                condaFile: 'conda::foo',
                targetImage: 'docker.io/some:image',
                userName: 'paolo',
                userEmail: 'paolo@seqera.io',
                userId: 100,
                requestIp: '10.20.30.40',
                startTime: Instant.now(),
                offsetId: '+02:00',
                duration: Duration.ofMinutes(1),
                platform: 'linux/amd64' )
        when:
        def binding = controller.renderBuildView(record)
        then:
        1 * buildLogService.fetchLogString('12345') >> new BuildLogService.BuildLog('log content', false)
        and:
        binding.build_id == '12345'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_image == 'docker.io/some:image'
        binding.build_user == 'paolo (ip: 10.20.30.40)'
        binding.build_platform == 'linux/amd64'
        binding.build_exit_status == null
        binding.build_platform == 'linux/amd64'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_format == 'Docker'
        binding.build_log_data == 'log content'
        binding.build_log_truncated == false
        binding.build_log_url == 'http://foo.com/v1alpha1/builds/12345/logs'
        binding.build_success == false
        binding.build_in_progress == true
        binding.build_failed == false
    }

    def 'should render in progress build page' () {
        given:
        def controller = new ViewController(serverUrl: 'http://foo.com', buildLogService: buildLogService)
        and:
        def record = new WaveBuildRecord(
                buildId: '12345',
                dockerFile: 'FROM foo',
                condaFile: 'conda::foo',
                targetImage: 'docker.io/some:image',
                userName: 'paolo',
                userEmail: 'paolo@seqera.io',
                userId: 100,
                requestIp: '10.20.30.40',
                startTime: Instant.now(),
                offsetId: '+02:00',
                duration: Duration.ofMinutes(1),
                exitStatus: 1,
                platform: 'linux/amd64' )
        when:
        def binding = controller.renderBuildView(record)
        then:
        1 * buildLogService.fetchLogString('12345') >> new BuildLogService.BuildLog('log content', false)
        and:
        binding.build_id == '12345'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_image == 'docker.io/some:image'
        binding.build_user == 'paolo (ip: 10.20.30.40)'
        binding.build_platform == 'linux/amd64'
        binding.build_exit_status == 1
        binding.build_platform == 'linux/amd64'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_format == 'Docker'
        binding.build_log_data == 'log content'
        binding.build_log_truncated == false
        binding.build_log_url == 'http://foo.com/v1alpha1/builds/12345/logs'
        binding.build_success == false
        binding.build_in_progress == false
        binding.build_failed == true
    }

    def 'should create binding' () {
        given:
        def controller = new ViewController(serverUrl: 'http://foo.com', buildLogService: buildLogService)
        and:
        def result = new WaveScanRecord(
                '12345',
                'bd-12345',
                'mr-12345',
                'cr-12345',
                'docker.io/some:image',
                Instant.now(),
                Duration.ofMinutes(1),
                ScanEntry.SUCCEEDED,
                [new ScanVulnerability('cve-1', 'HIGH', 'test vul', 'testpkg', '1.0.0', '1.1.0', 'http://vul/cve-1')],
                0,
                "Some scan logs"
        )
        when:
        def binding = controller.makeScanViewBinding(result)
        then:
        binding.scan_id == '12345'
        binding.scan_container_image == 'docker.io/some:image'
        binding.scan_time == formatTimestamp(result.startTime)
        binding.scan_duration == formatDuration(result.duration)
        binding.scan_succeeded
        binding.scan_exitcode == 0
        binding.scan_logs == "Some scan logs"
        binding.vulnerabilities == [new ScanVulnerability(id:'cve-1', severity:'HIGH', title:'test vul', pkgName:'testpkg', installedVersion:'1.0.0', fixedVersion:'1.1.0', primaryUrl:'http://vul/cve-1')]
        binding.build_id == 'bd-12345'
        binding.build_url == 'http://foo.com/view/builds/bd-12345'
        binding.mirror_id == 'mr-12345'
        binding.mirror_url == 'http://foo.com/view/mirrors/mr-12345'
        binding.request_id == 'cr-12345'
        binding.request_url == 'http://foo.com/view/containers/cr-12345'
    }

    def 'should render scan view page' () {
        given:
        def scan = new WaveScanRecord(
                '12345',
                'bd-12345',
                'mr-12345',
                'cr-12345',
                'docker.io/some:image',
                Instant.now(),
                Duration.ofMinutes(1),
                ScanEntry.SUCCEEDED,
                [new ScanVulnerability('cve-1', 'HIGH', 'test vul', 'testpkg', '1.0.0', '1.1.0', 'http://vul/cve-1')],
                0,
                "Some scan logs"
        )

        when:
        persistenceService.saveScanRecord(scan)
        and:
        def request = HttpRequest.GET("/view/scans/${scan.id}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(scan.id)
        response.body().contains(scan.buildId)
        response.body().contains(scan.mirrorId)
        response.body().contains(scan.requestId)
        response.body().contains(scan.containerImage)
    }

    def 'should render mirror page' () {
        given:
        def record1 = new MirrorResult(
                '12345',
                'sha256:abc12345',
                'docker.io/ubuntu:latest',
                'quay.io/ubuntu:latest',
                ContainerPlatform.DEFAULT,
                Instant.now(),
                "GMT",
                "sc-12456",
                MirrorResult.Status.COMPLETED,
                Duration.ofMinutes(1),
                0,
                'No logs'
        )

        when:
        persistenceService.saveMirrorResult(record1)
        and:
        def request = HttpRequest.GET("/view/mirrors/${record1.mirrorId}")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(record1.mirrorId)
        response.body().contains(record1.scanId)
        response.body().contains(record1.sourceImage)
        response.body().contains(record1.targetImage)
        response.body().contains(record1.digest)
    }

    @Unroll
    def 'should validate redirection check' () {
        given:
        def service = Mock(ContainerBuildService)
        def controller = new ViewController(buildService: service)

        when:
        def result = controller.shouldRedirect1(BUILD)
        then:
        result == EXPECTED

        where:
        BUILD           | EXPECTED
        '12345_1'       | null
        '12345-1'       | '/view/builds/12345_1'
        'foo-887766-1'  | '/view/builds/foo-887766_1'

    }


    def 'should validate redirect 2' () {
        given:
        def service = Mock(ContainerBuildService)
        def controller = new ViewController(buildService: service)

        when:
        def result = controller.shouldRedirect2(BUILD)
        then:
        result == EXPECTED
        TIMES * service.getLatestBuild(BUILD) >> LATEST

        where:
        BUILD           | TIMES | LATEST     | EXPECTED
        '12345_1'       | 0     | null       | null
        '12345'         | 1     | Mock(WaveBuildRecord) { buildId >> '12345_99' }       | '/view/builds/12345_99'
        '12345'         | 1     | Mock(WaveBuildRecord) { buildId >> 'xyz_99' }         | null
        'foo-887766'    | 1     | Mock(WaveBuildRecord) { buildId >> 'foo-887766_99' }  | '/view/builds/foo-887766_99'
        'foo-887766'    | 1     | Mock(WaveBuildRecord) { buildId >> 'foo-887766' }     | null

    }


}
