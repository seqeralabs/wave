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

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat
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
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import static io.seqera.wave.util.DataTimeUtils.formatDuration
import static io.seqera.wave.util.DataTimeUtils.formatTimestamp

import static io.seqera.wave.controller.ViewController.Colour

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Property(name = 'wave.server.url', value = 'http://foo.com')
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
    ContainerInspectService inspectService

    @Inject
    ContainerBuildService buildService

    @Value('${wave.server.url}')
    String serverUrl

    def 'should create build binding' () {
        given:
        def controller = new ViewController(serverUrl: serverUrl, buildLogService: buildLogService)
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
        1 * buildLogService.fetchCondaLockString('12345') >> 'conda lock content'
        and:
        binding.build_id == '12345'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_image == 'docker.io/some:image'
        binding.build_user == 'paolo'
        binding.build_platform == 'linux/amd64'
        binding.build_exit_status == 0
        binding.build_platform == 'linux/amd64'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_conda_lock_data == 'conda lock content'
        binding.build_format == 'Docker'
        binding.build_log_data == 'log content'
        binding.build_log_truncated == false
        binding.build_log_url == 'http://foo.com/v1alpha1/builds/12345/logs'
        binding.build_success == true
        binding.build_in_progress == false
        binding.build_failed == false
        binding.inspect_url == 'http://foo.com/view/inspect?image=docker.io/some:image&platform=linux/amd64'
    }

    def 'should render build page' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: '112233_1',
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
        and:
        response.body().contains(serverUrl)
    }

    def 'should render build page with conda file' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: '112233_1',
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
        response.body().contains(serverUrl)
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
        response.body().contains(token)
        and:
        response.body().contains(serverUrl)
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
        response.body().contains(serverUrl)
    }

    def 'should render in progress build page' () {
        given:
        def controller = new ViewController(serverUrl: serverUrl, buildLogService: buildLogService)
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
                duration: null,
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
        binding.build_user == 'paolo'
        binding.build_platform == 'linux/amd64'
        binding.build_exit_status == '-'
        binding.build_platform == 'linux/amd64'
        binding.build_containerfile == 'FROM foo'
        binding.build_condafile == 'conda::foo'
        binding.build_format == 'Docker'
        binding.build_log_data == 'log content'
        binding.build_log_truncated == false
        binding.build_log_url == 'http://foo.com/v1alpha1/builds/12345/logs'
        !binding.build_success
        binding.build_in_progress
        !binding.build_failed
    }

    def 'should render in progress build page' () {
        given:
        def controller = new ViewController(serverUrl: serverUrl, buildLogService: buildLogService)
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
        binding.build_user == 'paolo'
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
        def controller = new ViewController(serverUrl: serverUrl, buildLogService: buildLogService)
        and:
        def result = new WaveScanRecord(
                '12345',
                'bd-12345',
                'mr-12345',
                'cr-12345',
                'docker.io/some:image',
                ContainerPlatform.DEFAULT,
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
                ContainerPlatform.DEFAULT,
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
        response.body().contains(serverUrl)
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
                'pditommaso',
                'paolo@me.com',
                1,
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
        response.body().contains(record1.userName)
        response.body().contains(serverUrl)
    }

    def 'should render builds history page' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: 'bd-0727765dc72cee24_1',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                format: BuildFormat.DOCKER,
                platform: ContainerPlatform.DEFAULT_ARCH,
                exitStatus: 0 )
        and:
        def record2 = new WaveBuildRecord(
                buildId: 'bd-0727765dc72cee24_2',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                format: BuildFormat.DOCKER,
                platform: ContainerPlatform.DEFAULT_ARCH,
                exitStatus: 0 )
        and:
        def record3 = new WaveBuildRecord(
                buildId: 'bd-1234567890123456_2',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                format: BuildFormat.DOCKER,
                platform: ContainerPlatform.DEFAULT_ARCH,
                exitStatus: 0 )

        and:
        persistenceService.saveBuild(record1)
        persistenceService.saveBuild(record2)
        persistenceService.saveBuild(record3)

        when:
        def request = HttpRequest.GET("/view/builds/0727765dc72cee24")
        def response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(record1.buildId)
        response.body().contains(record2.buildId)
        !response.body().contains(record3.buildId)

        when:
        request = HttpRequest.GET("/view/builds/bd-0727765dc72cee24")
        response = client.toBlocking().exchange(request, String)
        then:
        response.body().contains(record1.buildId)
        response.body().contains(record2.buildId)
        !response.body().contains(record3.buildId)

        when:
        request = HttpRequest.GET("/view/builds/bd-0727765dc72cee24_2")
        response = client.toBlocking().exchange(request, String)
        then:
        !response.body().contains(record1.buildId)
        response.body().contains(record2.buildId)
        !response.body().contains(record3.buildId)

        when:
        request = HttpRequest.GET("/view/builds/07277")
        client.toBlocking().exchange(request, String)
        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        and:
        e.status.code == 404

    }

    def 'should render build page after fixing buildId' () {
        given:
        def record1 = new WaveBuildRecord(
                buildId: '112233_1',
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
        def request = HttpRequest.GET("/view/builds/112233-1")
        def response = client.toBlocking().exchange(request, String)

        then:
        response.body().contains(record1.buildId)
        and:
        response.body().contains('Container file')
        response.body().contains('FROM docker.io/test:foo')
        and:
        !response.body().contains('Conda file')
        and:
        response.body().contains(serverUrl)
    }

    def 'should return correct status for success build record'() {
        given:
        def result = new WaveBuildRecord(
                buildId: '112233_1',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        expect:
        ViewController.getStatus(result) == "SUCCEEDED"
    }

    def 'should return correct status for failed build record'() {
        given:
        def result = new WaveBuildRecord(
                buildId: '112233_1',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 1 )

        expect:
        ViewController.getStatus(result) == "FAILED"
    }

    def 'should return correct status for in progress build record'() {
        given:
        def result = new WaveBuildRecord(
                buildId: '112233_1',
                dockerFile: 'FROM docker.io/test:foo',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: null)

        expect:
        ViewController.getStatus(result) == "IN PROGRESS"
    }

    @Unroll
    def 'should validate redirection check' () {
        given:
        def service = Mock(ContainerBuildService)
        def controller = new ViewController(buildService: service)

        when:
        def result = controller.isBuildInvalidSuffix(BUILD)
        then:
        result == EXPECTED

        where:
        BUILD           | EXPECTED
        '12345_1'       | null
        '12345-1'       | '/view/builds/12345_1'
        'foo-887766-1'  | '/view/builds/foo-887766_1'
        and:
        'bd-12345_1'    | null
        'bd-12345-1'    | '/view/builds/bd-12345_1'
        'bd-887766-1'   | '/view/builds/bd-887766_1'
    }

    def 'should validate build id patten' () {
        given:
        def service = Mock(ContainerBuildService)
        def controller = new ViewController(buildService: service)

        when:
        def result = controller.isBuildMissingSuffix(BUILD)
        then:
        result == EXPECTED

        where:
        BUILD                   | EXPECTED
        null                    | false
        'bd-beac24afd572398d_1' | false // fully qualified
        and:
        'bd-beac24afd572398d'   | true  // prefix + container id
        'beac24afd572398d'      | true  // just the container id
        'beac24afd572398'       | true
        'beac24afd57239'        | true
        and:
        'beac24afd5723'         | false // too short
    }

    def 'should return binding map with scan results'() {
        given:
        def service = Mock(ContainerScanService)
        def controller = new ViewController(scanService: service)
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def PLATFORM = ContainerPlatform.of('linux/arm64')
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def CVE2 = new ScanVulnerability('cve-2', 'x2', 'title2', 'package2', 'version2', 'fixed2', 'url2')
        def CVE3 = new ScanVulnerability('cve-3', 'x3', 'title3', 'package3', 'version3', 'fixed3', 'url3')
        def CVE4 = new ScanVulnerability('cve-4', 'x4', 'title4', 'package4', 'version4', 'fixed4', 'url4')
        def scan1 = new WaveScanRecord('sc-1234567890abcdef_1', '100', null, null, CONTAINER_IMAGE, PLATFORM, Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3, CVE4], null, null)
        def scan2 = new WaveScanRecord('sc-1234567890abcdef_2', '101', null, null, CONTAINER_IMAGE, PLATFORM, Instant.now(), Duration.ofSeconds(10), 'FAILED', [], null, null)

        when:
        def result = controller.renderScansView([scan1, scan2])

        then:
        result.scan_container_image == 'docker.io/my/repo:container1234'
        result.scan_records.size() == 2
        result.scan_records[0].scan_id == 'sc-1234567890abcdef_1'
        result.scan_records[0].scan_status == 'SUCCEEDED'
        result.scan_records[0].scan_vuls_count == 4
        result.scan_records[1].scan_id == 'sc-1234567890abcdef_2'
        result.scan_records[1].scan_status == 'FAILED'
        result.scan_records[1].scan_vuls_count == '-'
    }

    @Unroll
    def 'should handle scan id suffix scenarios'() {
        given:
        def controller = new ViewController()

        expect:
        controller.isScanInvalidSuffix(SCANID) == EXPECTED

        where:
        SCANID          | EXPECTED
        'scan-12345-1'  | '/view/scans/scan-12345_1'
        'scan-abc-2'    | '/view/scans/scan-abc_2'
        'scan-xyz-99'   | '/view/scans/scan-xyz_99'
        'scan12345'     | null
        'scan-12345'    | '/view/scans/scan_12345'
        'scan_12345'    | null
        'scan-12345-'   | null
        'scan-12345-0'  | '/view/scans/scan-12345_0'
        'scan-12345-01' | '/view/scans/scan-12345_01'
    }

    def 'should find all scans' () {
        given:
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def PLATFORM = ContainerPlatform.of('linux/arm64')
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def CVE2 = new ScanVulnerability('cve-2', 'x2', 'title2', 'package2', 'version2', 'fixed2', 'url2')
        def CVE3 = new ScanVulnerability('cve-3', 'x3', 'title3', 'package3', 'version3', 'fixed3', 'url3')
        def CVE4 = new ScanVulnerability('cve-4', 'x4', 'title4', 'package4', 'version4', 'fixed4', 'url4')
        def scan1 = new WaveScanRecord('sc-1234567890abcde_1', '100', null, null, CONTAINER_IMAGE, PLATFORM, Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3, CVE4], null, null)
        def scan2 = new WaveScanRecord('sc-1234567890abcde_2', '101', null, null, CONTAINER_IMAGE, PLATFORM, Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3], null, null)

        when:
        persistenceService.saveScanRecord(scan1)
        persistenceService.saveScanRecord(scan2)
        and:
        def request = HttpRequest.GET("/view/scans/1234567890abcde")
        def response = client.toBlocking().exchange(request, String)

        then:
        response.body().contains(scan1.id)
        response.body().contains(scan2.id)
        and:
        response.body().contains('docker.io/my/repo:container1234')
        and:
        response.body().contains(serverUrl)
    }

    @Unroll
    def 'should validate scan id pattern'() {
        expect:
        new ViewController().isScanMissingSuffix(scanId) == expected

        where:
        scanId                  | expected
        'sc-1234567890abcdef'   | true
        'sc-1234567890abcde'    | true
        'sc-1234567890abcdef_01'| false
        null                    | false
        '1234567890abcdef'      | true
    }

    @Unroll
    def 'should return correct scan color based on vulnerabilities'() {
        expect:
        ViewController.getScanColor(VULNERABILITIES) == EXPEXTED_COLOR

        where:
        VULNERABILITIES                                                                             | EXPEXTED_COLOR
        [new ScanVulnerability(severity: 'LOW')]                                                    | new Colour('#dff0d8','#3c763d')
        [new ScanVulnerability(severity: 'MEDIUM')]                                                 | new Colour('#fff8c5','#000000')
        [new ScanVulnerability(severity: 'HIGH')]                                                   | new Colour('#ffe4e2','#e00404')
        [new ScanVulnerability(severity: 'CRITICAL')]                                               | new Colour('#ffe4e2','#e00404')
        [new ScanVulnerability(severity: 'LOW'), new ScanVulnerability(severity: 'MEDIUM')]         | new Colour('#fff8c5','#000000')
        [new ScanVulnerability(severity: 'LOW'), new ScanVulnerability(severity: 'HIGH')]           | new Colour('#ffe4e2','#e00404')
        [new ScanVulnerability(severity: 'MEDIUM'), new ScanVulnerability(severity: 'CRITICAL')]    | new Colour('#ffe4e2','#e00404')
        []                                                                                          | new Colour('#dff0d8','#3c763d')
    }
}
