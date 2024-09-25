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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
/**
 * Tests for ContainerScanServiceImpl
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerScanServiceImplTest extends Specification {

    @Inject ContainerScanServiceImpl containerScanService

    @Inject PersistenceService persistenceService

    @Inject ScanStateStore stateStore

    def 'should start scan successfully'() {
        given:
        def workDir = Files.createTempDirectory('test')
        def scanRequest = new ScanRequest('scan-1', 'build-1', null, 'ubuntu:latest', ContainerPlatform.of('linux/amd64'), workDir, Instant.now())

        when:
        containerScanService.scan(scanRequest)
        sleep 500 // wait for the scan record to be stored in db

        then:
        def scanRecord = persistenceService.loadScanRecord(scanRequest.id)
        scanRecord.id == scanRequest.id
        scanRecord.buildId == scanRequest.buildId

        cleanup:
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
        def KEY = 'scan-10'
        def jobService = Mock(JobService)
        def service = new ContainerScanServiceImpl(scanStore: stateStore, persistenceService: persistenceService, jobService: jobService)
        def job = JobSpec.scan(KEY, 'ubuntu:latest', Instant.now(), Duration.ofMinutes(1), workDir)
        def scan = new ScanResult(KEY, 'build-1', 'ubuntu:latest', Instant.now())

        when:
        service.onJobCompletion(job, scan, new JobState(JobState.Status.SUCCEEDED,0))
        then:
        with( stateStore.getScan(KEY)) {
            id == KEY
            buildId == 'build-1'
            containerImage == 'ubuntu:latest'
            status == 'SUCCEEDED'
            exitCode == 0
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-1'
            containerImage == 'ubuntu:latest'
            status == 'SUCCEEDED'
        }

        when:
        service.onJobCompletion(job, scan, new JobState(JobState.Status.FAILED, 10, "I'm broken"))
        then:
        with( stateStore.getScan(KEY) ) {
            id == KEY
            buildId == 'build-1'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
            exitCode == 10
            logs == "I'm broken"
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-1'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
        }

        cleanup:
        workDir?.deleteDir()
        stateStore.clear()
    }

    def 'should handle job error event and update scan record'() {
        given:
        def KEY = 'scan-20'
        def jobService = Mock(JobService)
        def service = new ContainerScanServiceImpl(scanStore: stateStore, persistenceService: persistenceService, jobService: jobService)
        def job = JobSpec.scan(KEY, 'ubuntu:latest', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def error = new Exception('Some error msg')
        def scan = new ScanResult(KEY, 'build-20', 'ubuntu:latest', Instant.now())

        when:
        service.onJobException(job, scan, error)
        then:
        with( stateStore.getScan(KEY) ) {
            id == KEY
            buildId == 'build-20'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
            exitCode == null
            logs == "Some error msg"
        }
        and:
        with( persistenceService.loadScanRecord(KEY) ) {
            id == KEY
            buildId == 'build-20'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
        }

        cleanup:
        stateStore.clear()
    }

    def 'should handle job timeout event and update scan record'() {
        given:
        def KEY = 'scan-30'
        def jobService = Mock(JobService)
        def service = new ContainerScanServiceImpl(scanStore: stateStore, persistenceService: persistenceService, jobService: jobService)
        def job = JobSpec.scan(KEY, 'ubuntu:latest', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def scan = new ScanResult(KEY, 'build-30', 'ubuntu:latest', Instant.now())

        when:
        service.onJobTimeout(job, scan)

        then:
        with( stateStore.getScan(KEY) ) {
            id == KEY
            buildId == 'build-30'
            containerImage == 'ubuntu:latest'
            status == 'FAILED'
            exitCode == null
            logs == "Container scan timed out"
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

}
