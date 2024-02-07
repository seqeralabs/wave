/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import java.time.temporal.ChronoUnit

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.metric.Metric

import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.test.SurrealDBTestContainer
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
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))

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
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealPersistenceService storage = applicationContext.getBean(SurrealPersistenceService)
        BuildRequest request = new BuildRequest(dockerFile,
                Path.of("."), "buildrepo", condaFile, null, BuildFormat.DOCKER,null, null, null,
                ContainerPlatform.of('amd64'),'{auth}', null, null, "127.0.0.1", null)
        BuildResult result = new BuildResult(request.id, -1, "ok", Instant.now(), Duration.ofSeconds(3))
        BuildEvent event = new BuildEvent(request, result)
        WaveBuildRecord build = WaveBuildRecord.fromEvent(event)

        when:
        storage.initializeDb()

        storage.saveBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/wave_build")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().requestIp == '127.0.0.1'
    }

    void "can't insert a build but ends without error"() {
        given:
        SurrealPersistenceService storage = applicationContext.getBean(SurrealPersistenceService)
        WaveBuildRecord build = new WaveBuildRecord(
                buildId: 'test',
                dockerFile: 'test',
                condaFile: 'test',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        when:
        surrealContainer.stop()

        storage.saveBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }

    void "an event insert a build"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        def storage = applicationContext.getBean(SurrealPersistenceService)
        storage.initializeDb()
        final service = applicationContext.getBean(SurrealPersistenceService)
        BuildRequest request = new BuildRequest("test", Path.of("."), "test", "test", null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', "test", null, "127.0.0.1", null)
        BuildResult result = new BuildResult(request.id, 0, "content", Instant.now(), Duration.ofSeconds(1))
        BuildEvent event = new BuildEvent(request, result)

        when:
        service.onBuildEvent(event)
        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/wave_build")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().requestIp == '127.0.0.1'
    }

    void "an event is not inserted if no database"() {
        given:
        surrealContainer.stop()
        final service = applicationContext.getBean(SurrealPersistenceService)
        BuildRequest request = new BuildRequest("test", Path.of("."), "test", "test", null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', "test", null, "127.0.0.1", null)
        BuildResult result = new BuildResult(request.id, 0, "content", Instant.now(), Duration.ofSeconds(1))
        BuildEvent event = new BuildEvent(request, result)

        when:
        service.onBuildEvent(event)
        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }

    def 'should load a build record' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)
        final request = new BuildRequest(
                'FROM foo:latest',
                Path.of("/some/path"),
                "buildrepo",
                'conda::recipe',
                null,
                BuildFormat.DOCKER,
                null,
                null,
                null,
                ContainerPlatform.of('amd64'),
                '{auth}',
                'docker.io/my/repo',
                null,
                "1.2.3.4",
                null )
        final result = new BuildResult(request.id, -1, "ok", Instant.now(), Duration.ofSeconds(3))
        final event = new BuildEvent(request, result)
        final record = WaveBuildRecord.fromEvent(event)

        and:
        persistence.saveBuildBlocking(record)

        when:
        def loaded = persistence.loadBuild(record.buildId)
        then:
        loaded == record
    }

    def 'should load a request record' () {
        given:
        def persistence = applicationContext.getBean(SurrealPersistenceService)
        and:
        def TOKEN = '123abc'
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
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
        def data = new ContainerRequestData( 1, 100, 'hello-world' )
        def wave = "wave.io/wt/$TOKEN/hello-world"
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def addr = "100.200.300.400"
        def exp = Instant.now().plusSeconds(3600)
        and:
        def request = new WaveContainerRecord(req, data, wave, user, addr, exp)

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

    def 'should return the correct build counts per metrics' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)

        def build1 = new WaveBuildRecord(
                buildId: 'test1',
                dockerFile: 'test1',
                condaFile: 'test1',
                targetImage: 'testImage1',
                userName: 'testUser1',
                userEmail: 'test1@xyz.com',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now().minus(1, ChronoUnit.DAYS),
                duration: Duration.ofSeconds(1),
                exitStatus: 1 )

        def build2 = new WaveBuildRecord(
                buildId: 'test2',
                dockerFile: 'test1',
                condaFile: 'test1',
                targetImage: 'testImage1',
                userName: 'testUser1',
                userEmail: 'test1@xyz.com',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        def build3 = new WaveBuildRecord(
                buildId: 'test3',
                dockerFile: 'test3',
                condaFile: 'test3',
                targetImage: 'testImage2',
                userName: null,
                userEmail: null,
                userId: null,
                requestIp: '127.0.0.2',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        and:
        persistence.saveBuild(build1)
        persistence.saveBuild(build2)
        persistence.saveBuild(build3)
        sleep 300

        expect: 'should return the correct counts per metric'
        persistence.getBuildCountByMetrics(Metric.ip, null, null, null) == [
                '127.0.0.1': 2,
                '127.0.0.2': 1
        ]
        persistence.getBuildCountByMetrics(Metric.user, null, null, null) == [
                'testUser1': 2,
                'unknown': 1
        ]
        persistence.getBuildCountByMetrics(Metric.image, null, null, null) == [
                'testImage1': 2,
                'testImage2': 1
        ]

        and: 'should return correct metric count for successful builds'
        persistence.getBuildCountByMetrics(Metric.ip, true, null, null) ==[
                '127.0.0.1': 1,
                '127.0.0.2': 1
        ]
        persistence.getBuildCountByMetrics(Metric.user, true, null, null) == [
                'testUser1': 1,
                'unknown': 1
        ]
        persistence.getBuildCountByMetrics(Metric.image, true, null, null) == [
                'testImage1': 1,
                'testImage2': 1
        ]

        and: 'should return correct metric count between two dates'
        persistence.getBuildCountByMetrics(Metric.ip, null, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == [
                '127.0.0.1': 1,
                '127.0.0.2': 1
        ]
        persistence.getBuildCountByMetrics(Metric.user, null, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == [
                'testUser1': 1,
                'unknown': 1
        ]
        persistence.getBuildCountByMetrics(Metric.image, null, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == [
                'testImage1': 1,
                'testImage2': 1
        ]

        and: 'should return total build count'
        persistence.getBuildCount(null, null, null) == 3
        persistence.getBuildCount(true, null, null) == 2

        and: 'should return total build count between two dates'
        persistence.getBuildCount( null, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == 2

    }

    def 'should return the correct pull counts per metrics' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)
        def TOKEN1 = '123abc'
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
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
        def data = new ContainerRequestData( 1, 100, 'hello-world' )
        def wave = "wave.io/wt/$TOKEN1/hello-world"
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def addr = "100.200.300.400"
        def exp = Instant.now().plusSeconds(3600)
        def request1 = new WaveContainerRecord(req, data, wave, user, addr, exp)

        def TOKEN2 = '1234abc'
        cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
        req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'abc',
                timestamp: Instant.now().minus(1, ChronoUnit.DAYS).toString()
        )
        data = new ContainerRequestData( 1, 100, 'hello-world' )
        wave = "wave.io/wt/$TOKEN2/hello-world"
        user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        addr = "100.200.300.400"
        exp = Instant.now().plusSeconds(3600)
        def request2 = new WaveContainerRecord(req, data, wave, user, addr, exp)

        def TOKEN3 = '12345abc'
        cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
        req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'lmn',
                timestamp: Instant.now().toString()
        )
        data = new ContainerRequestData( 1, 100, 'hello-wave-world' )
        wave = "wave.io/wt/$TOKEN3/hello-wave-world"
        user = null
        addr = "100.200.300.401"
        exp = Instant.now().plusSeconds(3600)
        def request3 = new WaveContainerRecord(req, data, wave, user, addr, exp)

        and:
        persistence.saveContainerRequest(TOKEN1, request1)
        persistence.saveContainerRequest(TOKEN2, request2)
        persistence.saveContainerRequest(TOKEN3, request3)
        sleep 300

        expect:'`should return the correct pull counts per metrics'
        persistence.getPullCountByMetrics(Metric.ip, null, null) ==[
                '100.200.300.400': 2,
                '100.200.300.401': 1
        ]
        persistence.getPullCountByMetrics(Metric.user, null, null) == [
                'foo': 2,
                'unknown': 1
        ]
        persistence.getPullCountByMetrics(Metric.image, null, null) == [
                'hello-world': 2,
                'hello-wave-world': 1
        ]

        and: 'should return the correct pull counts per metrics between two dates'
        persistence.getPullCountByMetrics(Metric.user, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == [
                'foo': 1,
                'unknown': 1
        ]
        persistence.getPullCountByMetrics(Metric.ip, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) ==[
                '100.200.300.400': 1,
                '100.200.300.401': 1
        ]
        persistence.getPullCountByMetrics(Metric.image, Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == [
                'hello-world': 1,
                'hello-wave-world': 1
        ]

        and: 'should return the correct pull'
        persistence.getPullCount(null, null) == 3

        and: 'should return the correct pull counts between two dates'
        persistence.getPullCount(Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()) == 2

        and:'should return distinct metric count'
        persistence.getDistinctMetrics(Metric.ip, null, null) == 2
        persistence.getDistinctMetrics(Metric.user, null, null) == 1
        persistence.getDistinctMetrics(Metric.image, null, null) == 2
    }


    def 'should get the correct build filter' () {
        expect:
        SurrealPersistenceService.getBuildMetricFilter(SUCCESS, STARTDATE, ENDDATE) == Filter

        where:
        SUCCESS | STARTDATE     | ENDDATE           | Filter
        null    | null          | null              | ''
        true    | null          | null              | 'where exitStatus = 0'
        false   | null          | null              | 'where exitStatus != 0'
        null    | Instant.now() | Instant.now()     | "where type::datetime(startTime) >= '$STARTDATE' and type::datetime(startTime) <= '$ENDDATE'"
        null    | Instant.now() | null              | ''
        null    | null          | Instant.now()     | ''
        true    | null          | Instant.now()     | "where exitStatus = 0"
        false   | Instant.now() | null              | "where exitStatus != 0"
        true    | Instant.now() | Instant.now()     | "where type::datetime(startTime) >= '$STARTDATE' and type::datetime(startTime) <= '$ENDDATE' and exitStatus = 0"
        false   | Instant.now() | Instant.now()     | "where type::datetime(startTime) >= '$STARTDATE' and type::datetime(startTime) <= '$ENDDATE' and exitStatus != 0"
    }

    def 'get the correct pull filter' () {
        expect:
        SurrealPersistenceService.getPullMetricFilter(STARTDATE, ENDDATE) == Filter

        where:
        STARTDATE     | ENDDATE           | Filter
        null          | null              | ''
        Instant.now() | null              | ''
        null          | Instant.now()     | ''
        Instant.now() | Instant.now()     | "where type::datetime(timestamp) >= '$STARTDATE' and type::datetime(timestamp) <= '$ENDDATE'"
    }
}
