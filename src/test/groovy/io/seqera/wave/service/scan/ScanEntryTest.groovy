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

package io.seqera.wave.service.scan

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.persistence.WaveScanRecord

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanEntryTest extends Specification {

    boolean nearly(Duration given, Duration expected) {
        given >= expected
        given < expected.plusSeconds(5)
    }

    def 'should create a result' () {
        given:
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        when:
        def result = ScanEntry.of(
                    scanId: '123',
                    buildId: 'build-123',
                    containerImage: 'docker.io/foo/bar:latest',
                    startTime: ts,
                    duration: elapsed,
                    status: 'DONE',
                    vulnerabilities: [ CVE1 ]
                )
        then:
        result.scanId == '123'
        result.buildId == 'build-123'
        result.containerImage == 'docker.io/foo/bar:latest'
        result.startTime == ts
        result.duration == Duration.ofMinutes(1)
        result.status == 'DONE'
        result.vulnerabilities == [ CVE1 ]
    }

    @Unroll
    def 'should validate completed' () {
        when:
        def result = ScanEntry.of(
                scanId: '123',
                buildId: 'build-123',
                containerImage: 'docker.io/foo/bar:latest',
                startTime: Instant.now(),
                duration: DURATION,
                status: 'DONE',
                vulnerabilities:  []
        )
        then:
        result.completed() == EXPECTED
        result.done() == EXPECTED

        where:
        DURATION                | EXPECTED
        null                    | false
        Duration.ofMinutes(1)   | true
    }

    @Unroll
    def 'should validate completed' () {
        when:
        def result = ScanEntry.of(
                scanId: '123',
                buildId: 'build-123',
                containerImage: 'docker.io/foo/bar:latest',
                startTime: Instant.now(),
                status: STATUS,
                vulnerabilities: []
        )
        then:
        result.succeeded() == EXPECTED

        where:
        STATUS              | EXPECTED
        'SOMETHING'         | false
        'SUCCEEDED'         | true
    }

    def 'should create result object' () {
        given:
        def cve1 = new ScanVulnerability('cve-1', 'HIGH', 'test vul', 'testpkg', '1.0.0', '1.1.0', 'http://vul/cve-1')
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        when:
        def result = ScanEntry.of(scanId: 'scan-123', buildId: 'build-123', containerImage: 'ubuntu:latest', startTime: ts, duration: Duration.ofMinutes(1), status: 'XYZ', vulnerabilities: [cve1])
        then:
        result.scanId == 'scan-123'
        result.buildId == 'build-123'
        result.containerImage == 'ubuntu:latest'
        result.startTime == ts
        result.duration == elapsed
        result.status == 'XYZ'
        result.vulnerabilities == [cve1]
    }

    def 'should create succeed result' () {
        given:
        def cve1 = new ScanVulnerability('cve-1', 'HIGH', 'test vul', 'testpkg', '1.0.0', '1.1.0', 'http://vul/cve-1')
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        and:
        def scan = ScanEntry.of(
                scanId: '12345',
                buildId: 'build-12345',
                containerImage: 'docker.io/some:image',
                startTime: ts,
                duration: elapsed,
                status: ScanEntry.SUCCEEDED,
                vulnerabilities: [cve1] )
        when:
        def result = scan.success(scan.vulnerabilities)
        then:
        result.scanId == '12345'
        result.buildId == 'build-12345'
        result.containerImage == 'docker.io/some:image'
        result.startTime == ts
        nearly(result.duration, elapsed)
        result.status == ScanEntry.SUCCEEDED
        result.vulnerabilities == [cve1]
    }

    def 'should create failed result from record' () {
        given:
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        and:
        def scan = ScanEntry.of(
                scanId: '12345',
                buildId: 'build-12345',
                containerImage: 'docker.io/some:image',
                startTime:  ts,
                duration: elapsed,
                status: ScanEntry.FAILED,
                vulnerabilities: [] )
        when:
        def result = scan.failure(1, "Oops something has failed")
        then:
        result.scanId == '12345'
        result.buildId == 'build-12345'
        result.containerImage == 'docker.io/some:image'
        result.startTime == ts
        nearly(result.duration, elapsed)
        result.status == ScanEntry.FAILED
        result.vulnerabilities == []
        result.exitCode == 1
        result.logs == "Oops something has failed"
    }

    def 'should create failed result from request' () {
        given:
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        and:
        def request = ScanRequest.of(
                scanId:'scan-123',
                buildId: 'build-345',
                configJson: 'config',
                targetImage: 'docker.io/foo/bar',
                platform: ContainerPlatform.DEFAULT,
                workDir: Path.of('/some/path'),
                creationTime: ts )
        when:
        def result = ScanEntry.failure(request)
        then:
        result.scanId == 'scan-123'
        result.buildId == 'build-345'
        result.containerImage == 'docker.io/foo/bar'
        result.startTime == ts
        nearly(result.duration, elapsed)
        result.status == ScanEntry.FAILED
        result.vulnerabilities == []
    }

    def 'should create scan pending' () {
        given:
        def ts = Instant.now()
        def request = ScanRequest.of(scanId: 'sc-123', buildId: 'bd-345', mirrorId: 'mr-1234', requestId: 'rq-123', targetImage: 'docker.io/foo/bar', creationTime: ts)
        when:
        def scan = ScanEntry.create(request)
        then:
        scan.scanId == 'sc-123'
        scan.buildId == 'bd-345'
        scan.mirrorId == 'mr-1234'
        scan.requestId == 'rq-123'
        scan.containerImage == 'docker.io/foo/bar'
        scan.startTime == ts
        scan.status == ScanEntry.PENDING
        scan.vulnerabilities == []
        scan.exitCode == null
        scan.logs == null
    }

    def 'should create vul summary' () {
        given:
        def s1 = Mock(ScanVulnerability) { severity>>'low' }
        def s2 = Mock(ScanVulnerability) { severity>>'low' }
        def s3 = Mock(ScanVulnerability) { severity>>'low' }
        def s4 = Mock(ScanVulnerability) { severity>>'high' }
        def s5 = Mock(ScanVulnerability) { severity>>'critical' }
        and:
        def entry = ScanEntry.of(vulnerabilities: [s1,s2,s3,s4,s5])

        when:
        def result = entry.summary()
        then:
        result == [low:3, high:1, critical:1]
    }

    def 'should create entry from record' () {
        given:
        def recrd = new WaveScanRecord(
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
        def entry = ScanEntry.of(recrd)
        then:
        entry.scanId == recrd.id
        entry.buildId == recrd.buildId
        entry.mirrorId == recrd.mirrorId
        entry.requestId == recrd.requestId
        entry.containerImage == recrd.containerImage
        entry.startTime == recrd.startTime
        entry.duration == recrd.duration
        entry.status == recrd.status
        entry.vulnerabilities == recrd.vulnerabilities
        entry.exitCode == recrd.exitCode
        entry.logs == recrd.logs
    }
}
