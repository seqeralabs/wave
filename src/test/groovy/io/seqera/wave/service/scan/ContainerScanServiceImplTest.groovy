/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.scan

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 * Tests for ContainerScanServiceImpl
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerScanServiceImplTest extends Specification {

    @Inject
    ContainerScanServiceImpl scanService

    @Inject
    PersistenceService persistenceService

    @Inject
    ScanStateStore stateStore

    def 'should start scan successfully'() {
        given:
        def KEY = 'scan-10'
        def workDir = Files.createTempDirectory('test')
        def scanRequest = ScanRequest.of(scanId: KEY, buildId:'build-1', targetImage: 'ubuntu:latest', platform: ContainerPlatform.of('linux/amd64'), workDir: workDir, creationTime: Instant.now())

        when:
        scanService.scan(scanRequest)
        sleep 500
        then:
        def scanRecord = stateStore.getScan(scanRequest.scanId)
        scanRecord.scanId == scanRequest.scanId
        scanRecord.buildId == scanRequest.buildId

        cleanup:
        stateStore.clear()
        workDir?.deleteDir()
    }

    def 'should handle job completion event and update scan record'() {
        given:
        def trivyDockerResulJson = """
            {"Results": [
              {
                 "Target": "redis (debian 12.0)",
                 "Class": "os-pkgs",
                 "Type": "debian",
                 "Vulnerabilities": [
        
                    {
                       "VulnerabilityID": "CVE-2010-4756",
                       "PkgID": "libc-bin@2.36-9",
                       "PkgName": "libc-bin",
                       "InstalledVersion": "2.36-9",
                       "FixedVersion": "1.1.1n-0+deb11u5",
                       "Layer": {
                          "Digest": "sha256:faef57eae888cbe4a5613eca6741b5e48d768b83f6088858aee9a5a2834f8151",
                          "DiffID": "sha256:24839d45ca455f36659219281e0f2304520b92347eb536ad5cc7b4dbb8163588"
                       },
                       "SeveritySource": "debian",
                       "PrimaryURL": "https://avd.aquasec.com/nvd/cve-2010-4756",
                       "DataSource": {
                          "ID": "debian",
                          "Name": "Debian Security Tracker",
                          "URL": "https://salsa.debian.org/security-tracker-team/security-tracker"
                       },
                       "Title": "glibc: glob implementation can cause excessive CPU and memory consumption due to crafted glob expressions",
                       "Description": "The glob implementation in the GNU C Library (aka glibc or libc6) allows remote authenticated users to cause a denial of service (CPU and memory consumption) via crafted glob expressions that do not match any pathnames, as demonstrated by glob expressions in STAT commands to an FTP daemon, a different vulnerability than CVE-2010-2632.",
                       "Severity": "LOW",
                       "CweIDs": [
                          "CWE-399"
                       ],
                       "CVSS": {
                          "nvd": {
                             "V2Vector": "AV:N/AC:L/Au:S/C:N/I:N/A:P",
                             "V2Score": 4
                          },
                          "redhat": {
                             "V2Vector": "AV:N/AC:L/Au:N/C:N/I:N/A:P",
                             "V2Score": 5
                          }
                       },
                       "References": [
                          "http://cxib.net/stuff/glob-0day.c",
                          "http://securityreason.com/achievement_securityalert/89",
                          "http://securityreason.com/exploitalert/9223",
                          "https://access.redhat.com/security/cve/CVE-2010-4756",
                          "https://bugzilla.redhat.com/show_bug.cgi?id=681681",
                          "https://bugzilla.redhat.com/show_bug.cgi?id=CVE-2010-4756",
                          "https://nvd.nist.gov/vuln/detail/CVE-2010-4756",
                          "https://www.cve.org/CVERecord?id=CVE-2010-4756"
                       ],
                       "PublishedDate": "2011-03-02T20:00:00Z",
                       "LastModifiedDate": "2021-09-01T12:15:00Z"
                    }]}
                 ]
              }      
        """
        def workDir = Files.createTempDirectory('test')
        def reportFile = workDir.resolve('report.json')
        Files.write(reportFile, trivyDockerResulJson.bytes)
        and:
        def KEY = 'scan-20'
        def jobService = Mock(JobService)
        def service = new ContainerScanServiceImpl(scanStore: stateStore, persistenceService: persistenceService, jobService: jobService)
        def job = JobSpec.scan(KEY, 'ubuntu:latest', Instant.now(), Duration.ofMinutes(1), workDir)
        def scan = ScanEntry.of(scanId: KEY, buildId: 'build-20', containerImage: 'ubuntu:latest', startTime: Instant.now())

        when:
        service.onJobCompletion(job, scan, new JobState(JobState.Status.SUCCEEDED,0))
        then:
        with( stateStore.getScan(KEY)) {
            scanId == KEY
            buildId == 'build-20'
            containerImage == 'ubuntu:latest'
            status == 'SUCCEEDED'
            exitCode == 0
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-20'
            containerImage == 'ubuntu:latest'
            status == 'SUCCEEDED'
        }

        when:
        service.onJobCompletion(job, scan, new JobState(JobState.Status.FAILED, 10, "I'm broken"))
        then:
        with( stateStore.getScan(KEY) ) {
            scanId == KEY
            buildId == 'build-20'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
            exitCode == 10
            logs == "I'm broken"
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-20'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
        }

        cleanup:
        workDir?.deleteDir()
        stateStore.clear()
    }

    def 'should handle job error event and update scan record'() {
        given:
        def KEY = 'scan-30'
        def jobService = Mock(JobService)
        def service = new ContainerScanServiceImpl(scanStore: stateStore, persistenceService: persistenceService, jobService: jobService)
        def job = JobSpec.scan(KEY, 'ubuntu:latest', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def error = new Exception('Some error msg')
        def scan = ScanEntry.of(scanId: KEY, buildId: 'build-30', containerImage: 'ubuntu:latest', startTime: Instant.now())

        when:
        service.onJobException(job, scan, error)
        then:
        with( stateStore.getScan(KEY) ) {
            scanId == KEY
            buildId == 'build-30'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
            exitCode == null
            logs == "Some error msg"
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-30'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
        }

        cleanup:
        stateStore.clear()
    }

    def 'should handle job timeout event and update scan record'() {
        given:
        def KEY = 'scan-40'
        def jobService = Mock(JobService)
        def service = new ContainerScanServiceImpl(scanStore: stateStore, persistenceService: persistenceService, jobService: jobService)
        def job = JobSpec.scan(KEY, 'ubuntu:latest', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def scan = ScanEntry.of(scanId: KEY, buildId: 'build-40', containerImage: 'ubuntu:latest', startTime: Instant.now())

        when:
        service.onJobTimeout(job, scan)

        then:
        with( stateStore.getScan(KEY) ) {
            scanId == KEY
            buildId == 'build-40'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
            exitCode == null
            logs == "Container scan timed out"
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-40'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
        }

        cleanup:
        stateStore.clear()
    }


    def 'should create a scan request' () {
        given:
        def scanService = new ContainerScanServiceImpl()
        def containerId = 'container1234'
        and:
        def workspace = Path.of('/some/workspace')
        def platform = ContainerPlatform.of('amd64')
        final build =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: 'FROM ubuntu',
                        workspace: workspace,
                        targetImage: 'docker.io/my/repo:container1234',
                        identity: PlatformId.NULL,
                        platform: platform,
                        configJson: '{"config":"json"}',
                        scanId: 'scan12345',
                        format: BuildFormat.DOCKER,
                        buildId: "${containerId}_1",
                )

        when:
        def scan = scanService.fromBuild(build)
        then:
        scan.scanId == build.scanId
        scan.buildId == build.buildId
        scan.workDir != build.workDir
        scan.configJson == build.configJson
        scan.targetImage == build.targetImage
        scan.platform == build.platform
        scan.workDir.startsWith(workspace)
    }

    def 'should create scan request from mirror' () {
        given:
        def scanService = new ContainerScanServiceImpl()
        and:
        def timestamp = Instant.now()
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/some/workspace'),
                '{config}',
                'sc-123',
                timestamp,
                "GMT",
                Mock(PlatformId)
        )
        
        when:
        ScanRequest scan = scanService.fromMirror(request)
        then:
        scan.scanId == 'sc-123'
        scan.mirrorId == request.mirrorId // build id is used to carry the mirror id
        scan.configJson == '{config}'
        scan.targetImage == 'target/foo'
        scan.platform == ContainerPlatform.DEFAULT
        scan.workDir == Path.of('/some/workspace/sc-123')
        scan.creationTime >= timestamp
    }

    def 'should create scan request from container request' () {
        given:
        def config = Mock(ScanConfig)
        def inspectService = Mock(ContainerInspectService)
        and:
        def scanService = new ContainerScanServiceImpl(inspectService: inspectService, config: config)
        def userId = Mock(PlatformId)
        and:
        def timestamp = Instant.now()
        def request = Mock(ContainerRequest)
        request.buildId >> 'bd-1234'
        request.requestId >> 'cr-abc123'
        request.scanId >> 'sc-345'
        request.containerImage >> 'docker.io/foo:bar'
        request.identity >> userId
        request.platform >> ContainerPlatform.DEFAULT

        when:
        ScanRequest scan = scanService.fromContainer(request)
        then:
        config.workspace >> Path.of('/some/workspace')
        inspectService.credentialsConfigJson(null,'docker.io/foo:bar',null,userId) >> '{docker json config}'
        and:
        scan.scanId == 'sc-345'
        scan.buildId == 'bd-1234'
        scan.mirrorId == null
        scan.requestId == 'cr-abc123'
        scan.configJson == '{docker json config}'
        scan.targetImage == 'docker.io/foo:bar'
        scan.platform == ContainerPlatform.DEFAULT
        scan.workDir == Path.of('/some/workspace/sc-345')
        scan.creationTime >= timestamp
    }

    @Unroll
    def 'should scan on container requests' () {
        given:
        def config = Mock(ScanConfig)
        def inspectService = Mock(ContainerInspectService)
        and:
        def scanService = Spy(new ContainerScanServiceImpl(inspectService: inspectService, config: config))
        def request = Mock(ContainerRequest)
        request.scanId >> SCAN_ID
        request.scanOnRequest >> ON_REQUEST
        request.dryRun >> DRY_RUN
        and:
        def scan = Mock(ScanRequest)

        when:
        scanService.scanOnRequest(request)
        then:
        RUN_TIMES * scanService.fromContainer(request) >> scan
        RUN_TIMES * scanService.scan(scan) >> null

        where:
        SCAN_ID | ON_REQUEST    | DRY_RUN   | RUN_TIMES
        null    | null          | false     | 0
        'sc-123'| null          | false     | 0
        'sc-123'| true          | false     | 1
        'sc-123'| true          | true      | 0
        null    | true          | false     | 0

    }

    @Unroll
    def 'should scan on cached build requests' () {
        given:
        def config = Mock(ScanConfig)
        def inspectService = Mock(ContainerInspectService)
        and:
        def scanService = Spy(new ContainerScanServiceImpl(inspectService: inspectService, config: config))
        scanService.existsScan(SCAN_ID)  >> EXISTS_SCAN
        and:
        def request = Mock(ContainerRequest)
        request.scanId >> SCAN_ID
        request.buildId >> BUILD_ID
        request.buildNew >> BUILD_NEW
        request.dryRun >> DRY_RUN
        and:
        def scan = Mock(ScanRequest)

        when:
        scanService.scanOnRequest(request)
        then:
        RUN_TIMES * scanService.fromContainer(request) >> scan
        RUN_TIMES * scanService.scan(scan) >> null

        where:
        SCAN_ID | BUILD_ID  | BUILD_NEW | DRY_RUN   | EXISTS_SCAN   | RUN_TIMES
        null    | null      | null      | null      | false         | 0
        'sc-123'| null      | null      | null      | false         | 0
        and:
        'sc-123'| 'bd-123'  | null      | null      | false         | 0
        'sc-123'| 'bd-123'  | true      | null      | false         | 0
        'sc-123'| 'bd-123'  | false     | null      | false         | 1
        'sc-123'| 'bd-123'  | false     | null      | true          | 0
        'sc-123'| 'bd-123'  | false     | true      | false         | 0
    }

}
