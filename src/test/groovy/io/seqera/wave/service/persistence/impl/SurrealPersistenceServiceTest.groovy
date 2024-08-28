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
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.test.SurrealDBTestContainer
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
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
                'container1234',
                dockerFile,
                condaFile,
                null,
                Path.of("."),
                'docker.io/my/repo:container1234',
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
        ).withBuildId('1')
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
                'container1234',
                'FROM foo:latest',
                'conda::recipe',
                null,
                Path.of("."),
                'docker.io/my/repo:container1234',
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
        ).withBuildId('123')
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

    def 'should save and update a build' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        final request = new BuildRequest(
                'container1234',
                'FROM foo:latest',
                'conda::recipe',
                null,
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
        ).withBuildId('123')
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
        def data = new ContainerRequestData(new PlatformId(user,100), 'hello-world' )
        def wave = "wave.io/wt/$TOKEN/hello-world"
        def addr = "100.200.300.400"
        def exp = Instant.now().plusSeconds(3600)
        and:
        def request = new WaveContainerRecord(req, data, wave, addr, exp)

        and:
        persistence.saveContainerRequest(TOKEN, request)
        and:
        sleep 200  // <-- the above request is async, give time to save it
        
        when:
        def loaded = persistence.loadContainerRequest(TOKEN)
        then:
        loaded == request


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
        def NOW = Instant.now()
        def SCAN_ID = 'a1'
        def BUILD_ID = '100'
        def CVE1 = new ScanVulnerability('cve-1', 'x1', 'title1', 'package1', 'version1', 'fixed1', 'url1')
        def CVE2 = new ScanVulnerability('cve-2', 'x2', 'title2', 'package2', 'version2', 'fixed2', 'url2')
        def CVE3 = new ScanVulnerability('cve-3', 'x3', 'title3', 'package3', 'version3', 'fixed3', 'url3')
        def scanRecord = new WaveScanRecord(SCAN_ID, BUILD_ID, NOW, Duration.ofSeconds(10), 'SUCCEEDED', [CVE1, CVE2, CVE3])
        when:
        persistence.createScanRecord(new WaveScanRecord(SCAN_ID, BUILD_ID, NOW))
        persistence.updateScanRecord(scanRecord)
        then:
        def result = persistence.loadScanRecord(SCAN_ID)
        and:
        result == scanRecord
        and:
        def scan = persistence.loadScanResult(SCAN_ID)
        scan.status == 'SUCCEEDED'
        scan.buildId == BUILD_ID
        scan.vulnerabilities == scanRecord.vulnerabilities

        when:
        def SCAN_ID2 = 'b2'
        def BUILD_ID2 = '102'
        def scanRecord2 = new WaveScanRecord(SCAN_ID2, BUILD_ID2, NOW, Duration.ofSeconds(20), 'FAILED', [CVE1])
        and:
        persistence.createScanRecord(new WaveScanRecord(SCAN_ID2, BUILD_ID2, NOW))
        // should save the same CVE into another build
        persistence.updateScanRecord(scanRecord2)
        then:
        def result2 = persistence.loadScanRecord(SCAN_ID2)
        and:
        result2 == scanRecord2
    }

    def 'should load a build records' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        final request1 = new BuildRequest(
                'container1',
                'FROM foo:latest',
                'conda::recipe',
                null,
                Path.of("."),
                'docker.io/my/repo:container1',
                new PlatformId(new User(id: 1, userName: 'foo', email: 'foo@seqera.io'), 100),
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
        ).withBuildId('1')
        def result1 = new BuildResult(request1.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        def event1 = new BuildEvent(request1, result1)
        def record1 = WaveBuildRecord.fromEvent(event1)
        final request2 = new BuildRequest(
                'container2',
                'FROM foo:latest',
                'conda::recipe',
                null,
                Path.of("."),
                'docker.io/my/repo:container2',
                new PlatformId(new User(id: 1, userName: 'foo', email: 'foo@seqera.io'), 100),
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
        ).withBuildId('2')
        def result2 = new BuildResult(request2.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        def event2 = new BuildEvent(request2, result2)
        def record2 = WaveBuildRecord.fromEvent(event2)

        and:
        persistence.saveBuild(record1)
        persistence.saveBuild(record2)

        when:
        sleep 100
        def builds1 = persistence.loadBuilds('docker.io/my/repo:container1', null)
        def builds2 = persistence.loadBuilds('docker.io/my/repo:container2', 'foo')
        def builds3 = persistence.loadBuilds(null, 'foo')
        def builds4 = persistence.loadBuilds(null, 'foo@seqera.io')

        then:
        builds1[0] == record1
        builds2 == [record1, record2]
        builds3 == [record1, record2]
        builds4 == [record1, record2]
    }

}
