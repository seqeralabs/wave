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
class ScanResultTest extends Specification {

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
        def result = new ScanResult(
                    '123',
                    'build-123',
                    'docker.io/foo/bar:latest',
                    ts,
                    elapsed,
                    'DONE',
                    [ CVE1 ]
                )
        then:
        result.id == '123'
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
        def result = new ScanResult(
                '123',
                'build-123',
                'docker.io/foo/bar:latest',
                Instant.now(),
                DURATION,
                'DONE',
                []
        )
        then:
        result.isCompleted() == EXPECTED

        where:
        DURATION                | EXPECTED
        null                    | false
        Duration.ofMinutes(1)   | true
    }

    @Unroll
    def 'should validate completed' () {
        when:
        def result = new ScanResult(
                '123',
                'build-123',
                'docker.io/foo/bar:latest',
                Instant.now(),
                null,
                STATUS,
                []
        )
        then:
        result.isSucceeded() == EXPECTED

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
        def result = ScanResult.create('scan-123', 'build-123', 'ubuntu:latest', ts,  Duration.ofMinutes(1), 'XYZ', [cve1])
        then:
        result.id == 'scan-123'
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
        def record = new WaveScanRecord(
                id: '12345',
                buildId: 'build-12345',
                containerImage: 'docker.io/some:image',
                startTime: ts,
                duration: elapsed,
                status: ScanResult.SUCCEEDED,
                vulnerabilities: [cve1] )
        when:
        def result = ScanResult.success(record, record.vulnerabilities)
        then:
        result.id == '12345'
        result.buildId == 'build-12345'
        result.containerImage == 'docker.io/some:image'
        result.startTime == ts
        nearly(result.duration, elapsed)
        result.status == ScanResult.SUCCEEDED
        result.vulnerabilities == [cve1]
    }

    def 'should create failed result from record' () {
        given:
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        and:
        def record = new WaveScanRecord(
                id: '12345',
                buildId: 'build-12345',
                containerImage: 'docker.io/some:image',
                startTime: ts,
                duration: elapsed,
                status: ScanResult.FAILED,
                vulnerabilities: [] )
        when:
        def result = ScanResult.failure(record)
        then:
        result.id == '12345'
        result.buildId == 'build-12345'
        result.containerImage == 'docker.io/some:image'
        result.startTime == ts
        nearly(result.duration, elapsed)
        result.status == ScanResult.FAILED
        result.vulnerabilities == []
    }

    def 'should create failed result from request' () {
        given:
        def elapsed = Duration.ofMinutes(1)
        def ts = Instant.now().minus(elapsed)
        and:
        def request = new ScanRequest(
                'scan-123',
                'build-345',
                'config',
                'docker.io/foo/bar',
                ContainerPlatform.DEFAULT,
                Path.of('/some/path'),
                ts )
        when:
        def result = ScanResult.failure(request)
        then:
        result.id == 'scan-123'
        result.buildId == 'build-345'
        result.containerImage == 'docker.io/foo/bar'
        result.startTime == ts
        nearly(result.duration, elapsed)
        result.status == ScanResult.FAILED
        result.vulnerabilities == []
    }
}