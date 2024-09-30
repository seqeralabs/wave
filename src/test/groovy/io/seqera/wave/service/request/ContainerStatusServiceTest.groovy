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

package io.seqera.wave.service.request

import java.time.Duration
import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerStatus
import io.seqera.wave.api.ScanLevel
import io.seqera.wave.api.ScanMode
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanEntry
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerStatusServiceTest extends Specification {

    def 'should create scan result' () {
        given:
        def service = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com')
        def request = Mock(ContainerRequest)
        def scan = Mock(ScanEntry)

        when:
        def res1 = service.scanResult(request, scan)
        then:
        scan.succeeded() >> false
        request.getScanId() >> 'scan-123'
        and:
        res1 == new ContainerStatusServiceImpl.StageResult(
                false,
                "Container security scan did not complete successfully",
                "http://foo.com/view/scans/scan-123"
        )

        when:
        def res2 = service.scanResult(request, scan)
        then:
        scan.succeeded() >> true
        scan.summary() >> [MEDIUM: 5]
        request.getScanId() >> 'scan-123'
        request.getScanLevels() >> null
        and:
        res2 == new ContainerStatusServiceImpl.StageResult(
                false,
                "Container security scan operation found one ore more vulnerabilities with severity: MEDIUM",
                "http://foo.com/view/scans/scan-123"
        )

        when:
        def res3 = service.scanResult(request, scan)
        then:
        scan.succeeded() >> true
        scan.summary() >> [HIGH:2, CRITICAL:1, MEDIUM: 5]
        request.getScanId() >> 'scan-123'
        request.getScanLevels() >> [ScanLevel.low, ScanLevel.medium]
        and:
        res3 == new ContainerStatusServiceImpl.StageResult(
                false,
                "Container security scan operation found one ore more vulnerabilities with severity: HIGH,CRITICAL",
                "http://foo.com/view/scans/scan-123"
        )

        when:
        def res4 = service.scanResult(request, scan)
        then:
        scan.succeeded() >> true
        scan.summary() >> [MEDIUM: 5]
        request.getScanId() >> 'scan-123'
        request.getScanLevels() >> [ScanLevel.low, ScanLevel.medium]
        and:
        res4 == new ContainerStatusServiceImpl.StageResult(
                true,
                "Container security scan operation found one ore more vulnerabilities that are compatible with requested security levels: LOW,MEDIUM",
                "http://foo.com/view/scans/scan-123"
        )

        when:
        def res5 = service.scanResult(request, scan)
        then:
        scan.succeeded() >> true
        scan.summary() >> [:]
        request.getScanId() >> 'scan-123'
        request.getScanLevels() >> [ScanLevel.low, ScanLevel.medium]
        and:
        res5 == new ContainerStatusServiceImpl.StageResult(true)

    }

    def 'should validate status for build running' () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // building is running
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime)
        requestData.buildId >> 'build-123'
        requestData.requestId >> requestId
        and:
        resp.id == requestId
        resp.status == ContainerStatus.BUILDING
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == null
        resp.creationTime == startTime
        resp.duration == null
        resp.succeeded == null
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }

    def "should validate status for build successful and no scan" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // building is successful, no scan
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        and:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == null
        resp.creationTime == startTime
        resp.duration == _1min
        resp.succeeded == true
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }

    def "should validate status build failed" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // building failed
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, false)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        and:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == null
        resp.creationTime == startTime
        resp.duration == _1min
        resp.succeeded == false
        resp.vulnerabilities == null
        resp.reason == "Container build did not complete successfully"
        resp.detailsUri == "http://foo.com/view/builds/build-123"
    }

    def "should validate status for build successful and scan running" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan is running
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of()
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry)
        then:
        resp.id == requestId
        resp.status == ContainerStatus.SCANNING
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == null
        resp.succeeded == null
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }
    def "should validate status for build successful and security scan successful" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan is successful
        when:
        def resp5 = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of()
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry) { getDuration()>>_2min; succeeded()>>true  }
        then:
        resp5.id == requestId
        resp5.status == ContainerStatus.READY
        resp5.buildId == "build-123"
        resp5.mirrorId == null
        resp5.scanId == 'scan-abc'
        resp5.creationTime == startTime
        resp5.duration == _1min + _2min // build + scan time
        resp5.succeeded == true
        resp5.vulnerabilities == null
        resp5.reason == null
        resp5.detailsUri == null
    }

    def "should validate status for build successful and scan failed" ( ) {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan failed
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of()
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry) { getDuration()>>_2min; succeeded()>>false  }
        then:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == _1min + _2min // build + scan time
        resp.succeeded == false
        resp.vulnerabilities == null
        resp.reason == "Container security scan did not complete successfully"
        resp.detailsUri == "http://foo.com/view/scans/scan-abc"
    }

    def "should validate status for build successful and scan with vulnerabilities" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan found vulnerabilities
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of()
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry) { getDuration()>>_2min; succeeded()>>true; summary()>>[HIGH:1]  }
        then:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == _1min + _2min // build + scan time
        resp.succeeded == false
        resp.vulnerabilities == [HIGH:1]
        resp.reason == "Container security scan operation found one ore more vulnerabilities with severity: HIGH"
        resp.detailsUri == "http://foo.com/view/scans/scan-abc"
    }

    def "should validate status for build successful and scan with not allowed vulnerabilities" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan has vulnerabilities that are allowed
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of(ScanLevel.medium,ScanLevel.high)
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry) { getDuration()>>_2min; succeeded()>>true; summary()>>[HIGH:1]  }
        then:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == _1min + _2min // build + scan time
        resp.succeeded == true
        resp.vulnerabilities == [HIGH:1]
        resp.reason == "Container security scan operation found one ore more vulnerabilities that are compatible with requested security levels: MEDIUM,HIGH"
        resp.detailsUri == "http://foo.com/view/scans/scan-abc"
    }

    def 'should validate status for build is successful and security scan is not enabled' () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.none
        requestData.scanLevels >> List.of(ScanLevel.medium,ScanLevel.high)
        and:
        0 * scanService.getScanState('scan-abc') >> null
        then:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == "build-123"
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == _1min
        resp.succeeded == true
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }

    def "should validate status for container request and scan with vulnerabilities ie. failed" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan found vulnerabilities
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >>  null
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of()
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry) { getDuration()>>_2min; succeeded()>>true; summary()>>[HIGH:1]; getStartTime()>>startTime  }
        then:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == null
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == _1min + _2min // request time + scan time
        resp.succeeded == false
        resp.vulnerabilities == [HIGH:1]
        resp.reason == "Container security scan operation found one ore more vulnerabilities with severity: HIGH"
        resp.detailsUri == "http://foo.com/view/scans/scan-abc"
    }

    def "should validate status for container request and scan with no vulnerabilities" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1sec = Duration.ofSeconds(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // container build is successful, security scan found vulnerabilities
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1sec, true)
        requestData.requestId >> requestId
        requestData.buildId >>  null
        requestData.scanId >> 'scan-abc'
        requestData.scanMode >> ScanMode.sync
        requestData.scanLevels >> List.of()
        and:
        scanService.getScanState('scan-abc') >> Mock(ScanEntry) { getDuration()>>_2min; succeeded()>>true; summary()>>null; getStartTime()>>startTime  }
        then:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == null
        resp.mirrorId == null
        resp.scanId == 'scan-abc'
        resp.creationTime == startTime
        resp.duration == _1sec + _2min // request time + scan time
        resp.succeeded == true
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }

    def 'should validate status for mirror running' () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // building is running
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.mirror >> true
        and:
        resp.id == requestId
        resp.status == ContainerStatus.BUILDING
        resp.buildId == null
        resp.mirrorId == "build-123"
        resp.scanId == null
        resp.creationTime == startTime
        resp.duration == null
        resp.succeeded == null
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }

    def "should validate status for mirror successful and no scan" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // building is successful, no scan
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, true)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.mirror >> true
        and:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == null
        resp.mirrorId == "build-123"
        resp.scanId == null
        resp.creationTime == startTime
        resp.duration == _1min
        resp.succeeded == true
        resp.vulnerabilities == null
        resp.reason == null
        resp.detailsUri == null
    }

    def "should validate status for mirror failed" () {
        given:
        def startTime = Instant.now().minusSeconds(10)
        def _1min = Duration.ofMinutes(1)
        def _2min = Duration.ofMinutes(2)
        def scanService = Mock(ContainerScanService)
        def store = Mock(ContainerRequestStore)
        def impl = new ContainerStatusServiceImpl(serverUrl: 'http://foo.com', requestStore: store, scanService:scanService)
        def service = Spy(impl)
        def requestId = '123456'
        def requestData = Mock(ContainerRequest)

        // building is successful, no scan
        when:
        def resp = service.getContainerStatus(requestId)
        then:
        store.get(requestId) >> requestData
        service.getContainerState(requestData) >> new ContainerState(startTime, _1min, false)
        requestData.requestId >> requestId
        requestData.buildId >> 'build-123'
        requestData.mirror >> true
        and:
        resp.id == requestId
        resp.status == ContainerStatus.READY
        resp.buildId == null
        resp.mirrorId == "build-123"
        resp.scanId == null
        resp.creationTime == startTime
        resp.duration == _1min
        resp.succeeded == false
        resp.vulnerabilities == null
        resp.reason == "Container mirror did not complete successfully"
        resp.detailsUri == "http://foo.com/view/mirrors/build-123"
    }
}
