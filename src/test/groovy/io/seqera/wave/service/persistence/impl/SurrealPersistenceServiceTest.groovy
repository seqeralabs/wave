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

package io.seqera.wave.service.persistence.impl

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.test.SurrealDBTestContainer
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import org.apache.commons.lang3.RandomStringUtils

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SurrealPersistenceServiceTest extends Specification implements SurrealDBTestContainer {

    ApplicationContext applicationContext

    String getSurrealDbURL() {
        "http://$surrealHostName:$surrealPort"
    }

    def setup() {
        restartDb()
        applicationContext = ApplicationContext.run([
                        surreal:['default': [
                                user     : 'root',
                                password : 'root',
                                ns       : 'test',
                                db       : 'test',
                                url      : surrealDbURL,
                                'init-db': false
                        ]]]
        , 'test', 'surrealdb')
    }

    def cleanup() {
        applicationContext.close()
    }

    void "can connect"() {
        given:
        def httpClient = HttpClient.create(new URL(surrealDbURL))

        when:
        def str = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.POST("/sql", "SELECT * FROM count()")
                                .headers(['ns': 'test', 'db': 'test', 'accept':'application/json'])
                                .basicAuth('root', 'root'), Map<String, String>)

        then:
        str.result.first() == 1
    }

    void "can insert an async build"() {
        given:
        final String dockerFile = """\
            FROM quay.io/nextflow/bash
            RUN echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
            ENV NOW=${System.currentTimeMillis()}
            """
        final String condaFile = """
            echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
        """
        def storage = applicationContext.getBean(SurrealPersistenceService)
        final request = new BuildRequest(
                containerId: 'container1234',
                containerFile: dockerFile,
                condaFile: condaFile,
                workspace:  Path.of("."),
                targetImage: 'docker.io/my/repo:container1234',
                identity: PlatformId.NULL,
                platform:  ContainerPlatform.of('amd64'),
                cacheRepository: 'docker.io/my/cache',
                ip: '127.0.0.1',
                configJson: '{"config":"json"}',
                scanId: 'scan12345',
                format:  BuildFormat.DOCKER,
                maxDuration:  Duration.ofMinutes(1),
                buildId: '12345_1',
        )
        def result = new BuildResult(request.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        def event = new BuildEvent(request, result)
        def build = WaveBuildRecord.fromEvent(event)

        when:
        storage.initializeDb()
        and:
        storage.saveBuild(build)
        then:
        sleep 100
        def stored = storage.loadBuild(request.buildId)
        stored.buildId == request.buildId
        stored.requestIp == '127.0.0.1'
    }

    def 'should load a build record' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        final request = new BuildRequest(
                containerId:  'container1234',
                containerFile:  'FROM foo:latest',
                condaFile:  'conda::recipe',
                workspace:  Path.of("."),
                targetImage:  'docker.io/my/repo:container1234',
                identity:  PlatformId.NULL,
                platform:  ContainerPlatform.of('amd64'),
                cacheRepository:  'docker.io/my/cache',
                ip: '127.0.0.1',
                configJson:  '{"config":"json"}',
                scanId:  'scan12345',
                format:  BuildFormat.DOCKER,
                maxDuration:  Duration.ofMinutes(1),
                buildId: '12345_1',
        )
        def result = new BuildResult(request.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        def event = new BuildEvent(request, result)
        def record = WaveBuildRecord.fromEvent(event)

        and:
        persistence.saveBuild(record)

        when:
        sleep 100
        def loaded = persistence.loadBuild(record.buildId)

        then:
        loaded == record
    }

    def 'should find latest succeed' () {
        given:
        def surreal = applicationContext.getBean(SurrealClient)
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        def auth = persistence.getAuthorization()
        def target = 'docker.io/my/target'
        def digest = 'sha256:12345'
        and:
        def request1 = new BuildRequest( targetImage: target, containerId: 'abc', buildId: 'bd-abc_1', workspace: Path.of('.'), startTime: Instant.now().minusSeconds(30), identity: PlatformId.NULL)
        def request2 = new BuildRequest( targetImage: target, containerId: 'abc', buildId: 'bd-abc_2', workspace: Path.of('.'), startTime: Instant.now().minusSeconds(20), identity: PlatformId.NULL)
        def request3 = new BuildRequest( targetImage: target, containerId: 'abc', buildId: 'bd-abc_3', workspace: Path.of('.'), startTime: Instant.now().minusSeconds(10), identity: PlatformId.NULL)
        and:
        def result1 = new BuildResult(request1.buildId, 1, "err", request1.startTime, Duration.ofSeconds(2), digest)
        def rec1 = WaveBuildRecord.fromEvent(new BuildEvent(request1, result1))
        surreal.insertBuild(auth, rec1)
        and:
        def result2 = new BuildResult(request2.buildId, 0, "ok", request2.startTime, Duration.ofSeconds(2), digest)
        def rec2 = WaveBuildRecord.fromEvent(new BuildEvent(request2, result2))
        surreal.insertBuild(auth, rec2)
        and:
        def result3 = new BuildResult(request3.buildId, 0, "ok", request3.startTime, Duration.ofSeconds(2), digest)
        def rec3 = WaveBuildRecord.fromEvent(new BuildEvent(request3, result3))
        surreal.insertBuild(auth, rec3)

        expect:
        persistence.loadBuildSucceed(target, digest) == rec3
    }

    def 'should find latest build' () {
        given:
        def surreal = applicationContext.getBean(SurrealClient)
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        def auth = persistence.getAuthorization()
        def request1 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_1' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(30), identity: PlatformId.NULL)
        def request2 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_2' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(20), identity: PlatformId.NULL)
        def request3 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_3' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(10), identity: PlatformId.NULL)

        def result1 = new BuildResult(request1.buildId, -1, "ok", request1.startTime, Duration.ofSeconds(2), null)
        surreal.insertBuild(auth, WaveBuildRecord.fromEvent(new BuildEvent(request1, result1)))
        and:
        def result2 = new BuildResult(request2.buildId, -1, "ok", request2.startTime, Duration.ofSeconds(2), null)
        surreal.insertBuild(auth, WaveBuildRecord.fromEvent(new BuildEvent(request2, result2)))
        and:
        def result3 = new BuildResult(request3.buildId, -1, "ok", request3.startTime, Duration.ofSeconds(2), null)
        surreal.insertBuild(auth, WaveBuildRecord.fromEvent(new BuildEvent(request3, result3)))

        expect:
        persistence.latestBuild('abc').buildId == 'bd-abc_3'
        persistence.latestBuild('bd-abc').buildId == 'bd-abc_3'
        persistence.latestBuild('xyz') == null
    }

    def 'should save and update a build' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        final request = new BuildRequest(
                containerId: 'container1234',
                containerFile:  'FROM foo:latest',
                condaFile:  'conda::recipe',
                workspace:  Path.of("/some/path"),
                targetImage:  'buildrepo:recipe-container1234',
                identity:  PlatformId.NULL,
                platform:  ContainerPlatform.of('amd64'),
                cacheRepository:  'docker.io/my/cache',
                ip:  '127.0.0.1',
                configJson:  '{"config":"json"}',
                scanId:  'scan12345',
                format:  BuildFormat.DOCKER,
                maxDuration:  Duration.ofMinutes(1),
                buildId: '12345_1'
        )
        and:
        def result = BuildResult.completed(request.buildId, 1, 'Hello', Instant.now().minusSeconds(60), 'xyz')

        and:
        def build1 = WaveBuildRecord.fromEvent(new BuildEvent(request, result))

        when:
        persistence.saveBuild(build1)
        sleep 100
        then:
        persistence.loadBuild(request.buildId) == build1

    }

    def 'should load a request record' () {
        given:
        def largeContainerFile = RandomStringUtils.random(25600, true, true)
        def persistence = applicationContext.getBean(SurrealPersistenceService)
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
        persistence.saveContainerRequest(request)
        and:
        sleep 200  // <-- the above request is async, give time to save it
        
        when:
        def loaded = persistence.loadContainerRequest(TOKEN)
        then:
        loaded == request
        loaded.containerFile == largeContainerFile

        // should update the record
        when:
        persistence.updateContainerRequest(TOKEN, new ContainerDigestPair('111', '222'))
        and:
        sleep 200
        then:
        def updated = persistence.loadContainerRequest(TOKEN)
        and:
        updated.sourceDigest == '111'
        updated.sourceImage == request.sourceImage
        and:
        updated.waveDigest == '222'
        updated.waveImage == request.waveImage

    }

    def 'should save a scan and load a result' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        def auth = persistence.getAuthorization()
        def surrealDb = applicationContext.getBean(SurrealClient)
        def NOW = Instant.now()
        def SCAN_ID = 'a1'
        def BUILD_ID = '100'
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def CVE2 = new ScanVulnerability('cve-2', 'x2', 'title2', 'package2', 'version2', 'fixed2', 'url2')
        def CVE3 = new ScanVulnerability('cve-3', 'x3', 'title3', 'package3', 'version3', 'fixed3', 'url3')
        def CVE4 = new ScanVulnerability('cve-4', 'x4', 'title4', 'package4', 'version4', 'fixed4', 'url4')
        def scan = new WaveScanRecord(SCAN_ID, BUILD_ID, null, null, CONTAINER_IMAGE, NOW, Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3], null, null)
        when:
        persistence.saveScanRecord(scan)
        then:
        def result = persistence.loadScanRecord(SCAN_ID)
        and:
        result == scan
        and:
        surrealDb
                .sqlAsMap(auth, "select * from wave_scan_vuln")
                .result
                .size() == 3
        and:
        persistence.existsScanRecord(SCAN_ID)

        when:
        def SCAN_ID2 = 'b2'
        def BUILD_ID2 = '102'
        def scanRecord2 = new WaveScanRecord(SCAN_ID2, BUILD_ID2, null, null, CONTAINER_IMAGE, NOW, Duration.ofSeconds(20), 'FAILED', [CVE1, CVE4], 1, "Error 'quote'")
        and:
        // should save the same CVE into another build
        persistence.saveScanRecord(scanRecord2)
        then:
        def result2 = persistence.loadScanRecord(SCAN_ID2)
        and:
        result2 == scanRecord2
        and:
        surrealDb
                .sqlAsMap(auth, "select * from wave_scan_vuln")
                .result
                .size() == 4
    }

    def 'should save a scan and check it exists' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        def auth = persistence.getAuthorization()
        def surrealDb = applicationContext.getBean(SurrealClient)
        def NOW = Instant.now()
        def SCAN_ID = 'a1'
        def BUILD_ID = '100'
        def CONTAINER_IMAGE = 'docker.io/my/repo:container1234'
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def scan = new WaveScanRecord(SCAN_ID, BUILD_ID, null, null, CONTAINER_IMAGE, NOW, Duration.ofSeconds(10), 'SUCCEEDED', [CVE1], null, null)

        expect:
        !persistence.existsScanRecord(SCAN_ID)

        when:
        persistence.saveScanRecord(scan)
        then:
        persistence.existsScanRecord(SCAN_ID)
    }

    //== mirror records tests

    void "should save and load a mirror record by id"() {
        given:
        def storage = applicationContext.getBean(SurrealPersistenceService)
        and:
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
        storage.initializeDb()
        and:
        def result = MirrorEntry.of(request).getResult()
        storage.saveMirrorResult(result)
        sleep 100

        when:
        def stored = storage.loadMirrorResult(request.mirrorId)
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
        def storage = applicationContext.getBean(SurrealPersistenceService)
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
        storage.initializeDb()
        and:
        def result1 = MirrorResult.of(request1).complete(1, 'err')
        def result2 = MirrorResult.of(request2).complete(0, 'ok')
        def result3 = MirrorResult.of(request3).complete(0, 'ok')
        storage.saveMirrorResult(result1)
        storage.saveMirrorResult(result2)
        storage.saveMirrorResult(result3)
        sleep 100

        when:
        def stored = storage.loadMirrorSucceed(target, digest)
        then:
        stored == result3
    }

    def 'should remove surreal table from json' () {
        given:
        def json = /{"id":"wave_request:1234abc", "this":"one", "that":123 }/
        expect:
        SurrealPersistenceService.patchSurrealId(json, "wave_request")
                == /{"id":"1234abc", "this":"one", "that":123 }/
    }

    def 'should save 50KB container and conda file' (){
        given:
        def data = RandomStringUtils.random(25600, true, true)
        def persistence = applicationContext.getBean(SurrealPersistenceService)
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
                Duration.ofMinutes(1)
        )
        and:
        def result = BuildResult.completed(request.buildId, 1, 'Hello', Instant.now().minusSeconds(60), 'xyz')

        and:
        def build1 = WaveBuildRecord.fromEvent(new BuildEvent(request, result))

        when:
        persistence.saveBuild(build1)
        sleep 100
        then:
        persistence.loadBuild(request.buildId) == build1
    }

    def 'should find all builds' () {
        given:
        def surreal = applicationContext.getBean(SurrealClient)
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        def auth = persistence.getAuthorization()
        def request1 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_1' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(30), identity: PlatformId.NULL)
        def request2 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_2' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(20), identity: PlatformId.NULL)
        def request3 = new BuildRequest( containerId: 'abc', buildId: 'bd-abc_3' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(10), identity: PlatformId.NULL)
        def request4 = new BuildRequest( containerId: 'abc', buildId: 'bd-xyz_3' , workspace: Path.of('.'), startTime: Instant.now().minusSeconds(0), identity: PlatformId.NULL)

        def result1 = new BuildResult(request1.buildId, -1, "ok", request1.startTime, Duration.ofSeconds(2), null)
        def record1 =WaveBuildRecord.fromEvent(new BuildEvent(request1, result1))
        surreal.insertBuild(auth, record1)
        and:
        def result2 = new BuildResult(request2.buildId, -1, "ok", request2.startTime, Duration.ofSeconds(2), null)
        def record2 = WaveBuildRecord.fromEvent(new BuildEvent(request2, result2))
        surreal.insertBuild(auth, record2)
        and:
        def result3 = new BuildResult(request3.buildId, -1, "ok", request3.startTime, Duration.ofSeconds(2), null)
        def record3 = WaveBuildRecord.fromEvent(new BuildEvent(request3, result3))
        surreal.insertBuild(auth, record3)
        and:
        def result4 = new BuildResult(request4.buildId, -1, "ok", request4.startTime, Duration.ofSeconds(2), null)
        def record4 = WaveBuildRecord.fromEvent(new BuildEvent(request4, result4))
        surreal.insertBuild(auth, record4)

        expect:
        persistence.allBuilds('abc') == [record3, record2, record1]
        and:
        persistence.allBuilds('bd-abc') == [record3, record2, record1]
        and:
        persistence.allBuilds('bd-abc_2') == [record2]
    }

}
