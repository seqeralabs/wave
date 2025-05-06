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

package io.seqera.wave.service.persistence.postgres

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildCompression
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'postgres', startApplication = true)
@Property(name = "datasources.default.driver-class-name", value = "org.testcontainers.jdbc.ContainerDatabaseDriver")
@Property(name = "datasources.default.url", value = "jdbc:tc:postgresql:///db")
class PostgresPersistentServiceTest extends Specification {

    @Inject
    PostgresPersistentService persistentService

    def 'should save and load a build record' () {
        given:
        def build1 = new WaveBuildRecord(
                buildId: 'test1',
                dockerFile: 'test1',
                condaFile: 'test1',
                targetImage: 'testImage1',
                userName: 'testUser1',
                userEmail: 'test1@xyz.com',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now().minus(1, ChronoUnit.DAYS) )

        when:
        persistentService.saveBuildAsync(build1)
        then:
        noExceptionThrown()
        
        when:
        def copy = persistentService.loadBuild('test1')
        then:
        copy == build1
    }

    def 'should save 50KB container and conda file' (){
        given:
        def data = RandomStringUtils.random(25600, true, true)
        final request = new BuildRequest(
                'container1234',
                data,
                data,
                Path.of("/some/path"),
                'buildrepo:recipe-container1234',
                PlatformId.NULL,
                ContainerPlatform.of('amd64'),
                'docker.io/my/cache',
                '127.0.0.1',
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                null,
                BuildFormat.DOCKER,
                Duration.ofMinutes(1),
                BuildCompression.estargz
        )
        and:
        def result = BuildResult.completed(request.buildId, 1, 'Hello', Instant.now().minusSeconds(60), 'xyz')

        and:
        def build1 = WaveBuildRecord.fromEvent(new BuildEvent(request, result))

        when:
        persistentService.saveBuildAsync(build1)
        then:
        persistentService.loadBuild(request.buildId) == build1
    }

    def 'should find latest succeed' () {
        given:
        def target = 'docker.io/my/target'
        def digest = 'sha256:12345'
        and:
        def request1 = new BuildRequest( targetImage: target, containerId: 'abc', buildId: 'bd-abc_1', workspace: Path.of('.'), startTime: Instant.now().minusSeconds(30), identity: PlatformId.NULL)
        def request2 = new BuildRequest( targetImage: target, containerId: 'abc', buildId: 'bd-abc_2', workspace: Path.of('.'), startTime: Instant.now().minusSeconds(20), identity: PlatformId.NULL)
        def request3 = new BuildRequest( targetImage: target, containerId: 'abc', buildId: 'bd-abc_3', workspace: Path.of('.'), startTime: Instant.now().minusSeconds(10), identity: PlatformId.NULL)
        and:
        def result1 = new BuildResult(request1.buildId, 1, "err", request1.startTime, Duration.ofSeconds(2), digest)
        def rec1 = WaveBuildRecord.fromEvent(new BuildEvent(request1, result1))
        persistentService.saveBuildAsync(rec1)
        and:
        def result2 = new BuildResult(request2.buildId, 0, "ok", request2.startTime, Duration.ofSeconds(2), digest)
        def rec2 = WaveBuildRecord.fromEvent(new BuildEvent(request2, result2))
        persistentService.saveBuildAsync(rec2)
        and:
        def result3 = new BuildResult(request3.buildId, 0, "ok", request3.startTime, Duration.ofSeconds(2), digest)
        def rec3 = WaveBuildRecord.fromEvent(new BuildEvent(request3, result3))
        persistentService.saveBuildAsync(rec3)

        expect:
        persistentService.loadBuildSucceed(target, digest) == rec3
    }

    def 'should find latest build' () {
        given:
        def request1 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_1' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(30), identity: PlatformId.NULL)
        def request2 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_2' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(20), identity: PlatformId.NULL)
        def request3 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_3' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(10), identity: PlatformId.NULL)

        def result1 = new BuildResult(request1.buildId, -1, "ok", request1.startTime, Duration.ofSeconds(2), null)
        persistentService.saveBuildAsync(WaveBuildRecord.fromEvent(new BuildEvent(request1, result1)))
        and:
        def result2 = new BuildResult(request2.buildId, -1, "ok", request2.startTime, Duration.ofSeconds(2), null)
        persistentService.saveBuildAsync(WaveBuildRecord.fromEvent(new BuildEvent(request2, result2)))
        and:
        def result3 = new BuildResult(request3.buildId, -1, "ok", request3.startTime, Duration.ofSeconds(2), null)
        persistentService.saveBuildAsync(WaveBuildRecord.fromEvent(new BuildEvent(request3, result3)))

        expect:
        persistentService.latestBuild('abc').buildId == 'bd-abc_3'
        persistentService.latestBuild('bd-abc').buildId == 'bd-abc_3'
        persistentService.latestBuild('xyz') == null
    }

    def 'should find all builds' () {
        given:
        def request1 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_1' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(30), identity: PlatformId.NULL)
        def request2 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_2' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(20), identity: PlatformId.NULL)
        def request3 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_3' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(10), identity: PlatformId.NULL)
        def request4 = new BuildRequest( containerId: 'abc', buildId: 'bd-xyz_3' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(0), identity: PlatformId.NULL)

        def result1 = new BuildResult(request1.buildId, -1, "ok", request1.startTime, Duration.ofSeconds(2), null)
        def record1 = WaveBuildRecord.fromEvent(new BuildEvent(request1, result1))
        persistentService.saveBuildAsync(record1)
        and:
        def result2 = new BuildResult(request2.buildId, -1, "ok", request2.startTime, Duration.ofSeconds(2), null)
        def record2 = WaveBuildRecord.fromEvent(new BuildEvent(request2, result2))
        persistentService.saveBuildAsync(record2)
        and:
        def result3 = new BuildResult(request3.buildId, -1, "ok", request3.startTime, Duration.ofSeconds(2), null)
        def record3 = WaveBuildRecord.fromEvent(new BuildEvent(request3, result3))
        persistentService.saveBuildAsync(record3)
        and:
        def result4 = new BuildResult(request4.buildId, -1, "ok", request4.startTime, Duration.ofSeconds(2), null)
        def record4 = WaveBuildRecord.fromEvent(new BuildEvent(request4, result4))
        persistentService.saveBuildAsync(record4)

        expect:
        persistentService.allBuilds('abc') == [record3, record2, record1]
        and:
        persistentService.allBuilds('bd-abc') == [record3, record2, record1]
        and:
        persistentService.allBuilds('ab') == null
    }

    // ===== --- container request---- =====

    def 'should load a request record' () {
        given:
        def largeContainerFile = RandomStringUtils.random(25600, true, true)
        and:
        def TOKEN = '123abc'
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'],
                layers: [ new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/v2.2.8-amd64.json')])
        def req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'xyz',
                timestamp: Instant.now().toString()
        )
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def data = ContainerRequest.of(requestId: TOKEN, identity: new PlatformId(user,100), containerImage: 'hello-world', containerFile: largeContainerFile )
        def wave = "wave.io/wt/$TOKEN/hello-world"
        def addr = "100.200.300.400"
        def exp = Instant.now().plusSeconds(3600)
        and:
        def request = new WaveContainerRecord(req, data, wave, addr, exp)
        and:
        persistentService.saveContainerRequestAsync(request)

        when:
        def loaded = persistentService.loadContainerRequest(TOKEN)
        then:
        loaded == request
        loaded.containerFile == largeContainerFile

        // should update the record
        when:
        persistentService.updateContainerRequestAsync(TOKEN, new ContainerDigestPair('111', '222'))
        then:
        def updated = persistentService.loadContainerRequest(TOKEN)
        and:
        updated.sourceDigest == '111'
        updated.sourceImage == request.sourceImage
        and:
        updated.waveDigest == '222'
        updated.waveImage == request.waveImage
    }


    // ===== --- mirror records ---- =====

    void "should save and load a mirror record by id"() {
        given:
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/workspace'),
                '{auth json}',
                'scan-123',
                Instant.now(),
                "GMT",
                Mock(PlatformId)
        )
        and:
        def result = MirrorEntry.of(request).getResult()
        persistentService.saveMirrorResultAsync(result)

        when:
        def stored = persistentService.loadMirrorResult(request.mirrorId)
        then:
        stored == result
    }

    void "should save and load a mirror record by target and digest"() {
        given:
        def digest = 'sha256:12345'
        def timestamp = Instant.now()
        def source = 'source.io/foo'
        def target = 'target.io/foo'
        and:
        def request1 = MirrorRequest.create(
                source,
                target,
                digest,
                ContainerPlatform.DEFAULT,
                Path.of('/workspace'),
                '{auth json}',
                'scan-1',
                timestamp.minusSeconds(180),
                "GMT",
                Mock(PlatformId) )
        and:
        def request2 = MirrorRequest.create(
                source,
                target,
                digest,
                ContainerPlatform.DEFAULT,
                Path.of('/workspace'),
                '{auth json}',
                'scan-2',
                timestamp.minusSeconds(120),
                "GMT",
                Mock(PlatformId) )
        and:
        def request3 = MirrorRequest.create(
                source,
                target,
                digest,
                ContainerPlatform.DEFAULT,
                Path.of('/workspace'),
                '{auth json}',
                'scan-3',
                timestamp.minusSeconds(60),
                "GMT",
                Mock(PlatformId) )

        and:
        def result1 = MirrorResult.of(request1).complete(1, 'err')
        def result2 = MirrorResult.of(request2).complete(0, 'ok')
        def result3 = MirrorResult.of(request3).complete(0, 'ok')
        persistentService.saveMirrorResultAsync(result1)
        persistentService.saveMirrorResultAsync(result2)
        persistentService.saveMirrorResultAsync(result3)

        when:
        def stored = persistentService.loadMirrorSucceed(target, digest)
        then:
        stored == result3
    }

    // ======== ----- scan records ----- =====

    def 'should save a scan and load a result' () {
        given:
        def NOW = Instant.now()
        def SCAN_ID = 'a1'
        def BUILD_ID = '100'
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def PLATFORM = ContainerPlatform.of('linux/amd64')
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def CVE2 = new ScanVulnerability('cve-2', 'x2', 'title2', 'package2', 'version2', 'fixed2', 'url2')
        def CVE3 = new ScanVulnerability('cve-3', 'x3', 'title3', 'package3', 'version3', 'fixed3', 'url3')
        def CVE4 = new ScanVulnerability('cve-4', 'x4', 'title4', 'package4', 'version4', 'fixed4', 'url4')
        def scan = new WaveScanRecord(SCAN_ID, BUILD_ID, null, null, CONTAINER_IMAGE, PLATFORM, NOW, Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3], null, null, null)
        when:
        persistentService.saveScanRecordAsync(scan)
        then:
        def result = persistentService.loadScanRecord(SCAN_ID)
        and:
        result == scan
        and:
        persistentService.existsScanRecord(SCAN_ID)

        when:
        def SCAN_ID2 = 'b2'
        def BUILD_ID2 = '102'
        def scanRecord2 = new WaveScanRecord(SCAN_ID2, BUILD_ID2, null, null, CONTAINER_IMAGE, PLATFORM, NOW, Duration.ofSeconds(20), 'FAILED', [CVE1, CVE4], 1, "Error 'quote'", null)
        and:
        // should save the same CVE into another build
        persistentService.saveScanRecordAsync(scanRecord2)
        then:
        def result2 = persistentService.loadScanRecord(SCAN_ID2)
        and:
        result2 == scanRecord2
    }

    def 'should save a scan and check it exists' () {
        given:
        def NOW = Instant.now()
        def SCAN_ID = 'a1'
        def BUILD_ID = '100'
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def PLATFORM = ContainerPlatform.of('linux/amd64')
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def scan = new WaveScanRecord(SCAN_ID, BUILD_ID, null, null, CONTAINER_IMAGE, PLATFORM, NOW, Duration.ofSeconds(10), 'SUCCEEDED', [CVE1], null, null, null)

        expect:
        !persistentService.existsScanRecord(SCAN_ID)

        when:
        persistentService.saveScanRecordAsync(scan)
        then:
        persistentService.existsScanRecord(SCAN_ID)
    }

    def 'should find all scans' () {
        given:
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def PLATFORM = ContainerPlatform.of('linux/amd64')
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def CVE2 = new ScanVulnerability('cve-2', 'x2', 'title2', 'package2', 'version2', 'fixed2', 'url2')
        def CVE3 = new ScanVulnerability('cve-3', 'x3', 'title3', 'package3', 'version3', 'fixed3', 'url3')
        def CVE4 = new ScanVulnerability('cve-4', 'x4', 'title4', 'package4', 'version4', 'fixed4', 'url4')
        def scan1 = new WaveScanRecord('sc-1234567890abcdef_1', '100', null, null, CONTAINER_IMAGE, PLATFORM, Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3, CVE4], null, null, null)
        def scan2 = new WaveScanRecord('sc-1234567890abcdef_2', '101', null, null, CONTAINER_IMAGE, PLATFORM,Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3], null, null, null)
        def scan3 = new WaveScanRecord('sc-1234567890abcdef_3', '102', null, null, CONTAINER_IMAGE, PLATFORM,Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2], null, null, null)
        def scan4 = new WaveScanRecord('sc-01234567890abcdef_4', '103', null, null, CONTAINER_IMAGE, PLATFORM,Instant.now(), Duration.ofSeconds(10), 'SUCCEEDED', [CVE1], null, null, null)

        when:
        persistentService.saveScanRecordAsync(scan1)
        persistentService.saveScanRecordAsync(scan2)
        persistentService.saveScanRecordAsync(scan3)
        persistentService.saveScanRecordAsync(scan4)

        then:
        persistentService.allScans("1234567890abcdef") == [scan3, scan2, scan1]
        and:
        persistentService.allScans("1234567890") == null
    }
}
