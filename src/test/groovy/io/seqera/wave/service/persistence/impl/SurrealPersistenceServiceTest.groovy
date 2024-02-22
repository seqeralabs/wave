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
import java.time.temporal.ChronoUnit

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
import io.seqera.wave.service.metric.Metric
import io.seqera.wave.service.metric.MetricFilter
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
                Path.of("."), "buildrepo", condaFile, null, BuildFormat.DOCKER, PlatformId.NULL, null, null,
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
        BuildRequest request = new BuildRequest("test", Path.of("."), "test", "test", null, BuildFormat.DOCKER, Mock(PlatformId), null, null, ContainerPlatform.of('amd64'),'{auth}', "test", null, "127.0.0.1", null)
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
        BuildRequest request = new BuildRequest("test", Path.of("."), "test", "test", null, BuildFormat.DOCKER, Mock(PlatformId), null, null, ContainerPlatform.of('amd64'),'{auth}', "test", null, "127.0.0.1", null)
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
                PlatformId.NULL,
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

    def 'should return the correct builds count per metric' () {
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

        expect: 'should return the correct builds count per metric'
        def emptyFilter = new MetricFilter.Builder().build()
        persistence.getBuildsCountByMetric(Metric.ip, emptyFilter) == [
                '127.0.0.1': 2,
                '127.0.0.2': 1
        ]
        persistence.getBuildsCountByMetric(Metric.user, emptyFilter) == [
                'test1@xyz.com': 2,
                'anonymous': 1
        ]
        persistence.getBuildsCountByMetric(Metric.image, emptyFilter) == [
                'testImage1': 2,
                'testImage2': 1
        ]

        and: 'should return successful builds count per metric'
        def successFilter = new MetricFilter.Builder().success(true).build()
        persistence.getBuildsCountByMetric(Metric.ip, successFilter) ==[
                '127.0.0.1': 1,
                '127.0.0.2': 1
        ]
        persistence.getBuildsCountByMetric(Metric.user, successFilter) == [
                'test1@xyz.com': 1,
                'anonymous': 1
        ]
        persistence.getBuildsCountByMetric(Metric.image, successFilter) == [
                'testImage1': 1,
                'testImage2': 1
        ]

        and: 'should return correct builds count per metric between given dates'
        def datesFilter = new MetricFilter.Builder().dates(Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()).build()
        persistence.getBuildsCountByMetric(Metric.ip, datesFilter) == [
                '127.0.0.1': 1,
                '127.0.0.2': 1
        ]
        persistence.getBuildsCountByMetric(Metric.user, datesFilter) == [
                'test1@xyz.com': 1,
                'anonymous': 1
        ]
        persistence.getBuildsCountByMetric(Metric.image, datesFilter) == [
                'testImage1': 1,
                'testImage2': 1
        ]

        and: 'should return limited number of builds count per metric records as defined by limit filter'
        def limitFilter = new MetricFilter.Builder().limit(1).build()
        persistence.getBuildsCountByMetric(Metric.ip, limitFilter) == ['127.0.0.1': 2]
        persistence.getBuildsCountByMetric(Metric.user, limitFilter) == ['test1@xyz.com': 2]
        persistence.getBuildsCountByMetric(Metric.image, limitFilter) == ['testImage1': 2]

        and: 'should return total builds count'

        persistence.getBuildsCount(emptyFilter) == 3
        persistence.getBuildsCount(successFilter) == 2

        and: 'should return total builds count between given dates'
        persistence.getBuildsCount(datesFilter) == 2

    }

    def 'should return the correct pulls count per metric and distinct metric count' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)
        def TOKEN1 = '123abc'
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
        def wave = "wave.io/wt/$TOKEN1/hello-world"
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def data = new ContainerRequestData(new PlatformId(user,100), 'hello-world' )
        def addr = "100.200.300.400"
        def exp = Instant.now().plusSeconds(3600)
        def request1 = new WaveContainerRecord(req, data, wave, addr, exp)

        def TOKEN2 = '1234abc'
        cfg = new ContainerConfig(entrypoint: ['/opt/fusion'],
                 layers: [ new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/v2.2.8-amd64.json')])
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
        wave = "wave.io/wt/$TOKEN2/hello-world"
        user = new User(id: 1, userName: 'bar', email: 'foo@gmail.com')
        data = new ContainerRequestData(new PlatformId(user,100), 'hello-world' )
        addr = "100.200.300.400"
        exp = Instant.now().plusSeconds(3600)
        def request2 = new WaveContainerRecord(req, data, wave, addr, exp)

        def TOKEN3 = '12345abc'
        cfg = new ContainerConfig(entrypoint: ['sh'])
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
        wave = "wave.io/wt/$TOKEN3/hello-wave-world"
        user = null
        data = new ContainerRequestData(new PlatformId(user,100), 'hello-wave-world' )
        addr = "100.200.300.401"
        exp = Instant.now().plusSeconds(3600)
        def request3 = new WaveContainerRecord(req, data, wave, addr, exp)

        and:
        persistence.saveContainerRequest(TOKEN1, request1)
        persistence.saveContainerRequest(TOKEN2, request2)
        persistence.saveContainerRequest(TOKEN3, request3)
        sleep 300

        expect: 'should return the correct pulls count per metric'
        def emptyFilter = new MetricFilter.Builder().build()
        persistence.getPullsCountByMetric(Metric.ip, emptyFilter) ==[
                '100.200.300.400': 2,
                '100.200.300.401': 1
        ]
        persistence.getPullsCountByMetric(Metric.user, emptyFilter) == [
                'foo@gmail.com': 2,
                'anonymous': 1
        ]
        persistence.getPullsCountByMetric(Metric.image, emptyFilter) == [
                'hello-world': 2,
                'hello-wave-world': 1
        ]

        and: 'should return the correct pulls count per metric between given dates'
        def datesFilter = new MetricFilter.Builder().dates(Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()).build()
        persistence.getPullsCountByMetric(Metric.user, datesFilter) == [
                'foo@gmail.com': 1,
                'anonymous': 1
        ]
        persistence.getPullsCountByMetric(Metric.ip, datesFilter) ==[
                '100.200.300.400': 1,
                '100.200.300.401': 1
        ]
        persistence.getPullsCountByMetric(Metric.image, datesFilter) == [
                'hello-world': 1,
                'hello-wave-world': 1
        ]

        and: 'should return the correct pulls count per metric for containers with fusion'
        def fusionTrueFilter = new MetricFilter.Builder().fusion(true).build()
        persistence.getPullsCountByMetric(Metric.user, fusionTrueFilter) == [
                'foo@gmail.com': 2
        ]
        persistence.getPullsCountByMetric(Metric.ip, fusionTrueFilter) ==[
                '100.200.300.400': 2
        ]
        persistence.getPullsCountByMetric(Metric.image, fusionTrueFilter) == [
                'hello-world': 2
        ]

        and: 'should return the correct pulls count per metric for containers without fusion'
        def fusionFalseFilter = new MetricFilter.Builder().fusion(false).build()
        persistence.getPullsCountByMetric(Metric.user, fusionFalseFilter) == [
                'anonymous': 1
        ]
        persistence.getPullsCountByMetric(Metric.ip, fusionFalseFilter) ==[
                '100.200.300.401': 1
        ]
        persistence.getPullsCountByMetric(Metric.image, fusionFalseFilter) == [
                'hello-wave-world': 1
        ]

        and:'`should return limited number of pulls count per metric records specified by limit filter'
        def limitFilter = new MetricFilter.Builder().limit(1).build()
        persistence.getPullsCountByMetric(Metric.ip, limitFilter) ==['100.200.300.400': 2]
        persistence.getPullsCountByMetric(Metric.user, limitFilter) == ['foo@gmail.com': 2]
        persistence.getPullsCountByMetric(Metric.image, limitFilter) == ['hello-world': 2]

        and: 'should return the correct number of pulls'
        persistence.getPullsCount(emptyFilter) == 3

        and: 'should return the correct number of pulls between two dates'
        persistence.getPullsCount(datesFilter) == 2

        and: 'should return the correct number of pulls for containers with fusion'
        persistence.getPullsCount(fusionTrueFilter) == 2

        and: 'should return the correct number of pulls for containers without fusion'
        persistence.getPullsCount(fusionFalseFilter) == 1

        and:'should return distinct metric count'
        persistence.getDistinctMetrics(Metric.ip, emptyFilter) == 2
        persistence.getDistinctMetrics(Metric.user, emptyFilter) == 1
        persistence.getDistinctMetrics(Metric.image, emptyFilter) == 2

        and:'should return distinct metric count between dates'
        persistence.getDistinctMetrics(Metric.ip, datesFilter) == 2
        persistence.getDistinctMetrics(Metric.user, datesFilter) == 1
        persistence.getDistinctMetrics(Metric.image, datesFilter) == 2

        and:'should return distinct metric count for containers with fusion'
        persistence.getDistinctMetrics(Metric.ip, fusionTrueFilter) == 1
        persistence.getDistinctMetrics(Metric.user, fusionTrueFilter) == 1
        persistence.getDistinctMetrics(Metric.image, fusionTrueFilter) == 1

        and:'should return distinct metric count for containers without fusion'
        persistence.getDistinctMetrics(Metric.ip, fusionFalseFilter) == 1
        persistence.getDistinctMetrics(Metric.user, fusionFalseFilter) == 0
        persistence.getDistinctMetrics(Metric.image, fusionFalseFilter) == 1
    }

    def 'should get empty map for builds count and pulls count per metric when no record found' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)
        expect:
        def emptyFilter = new MetricFilter.Builder().build()
        persistence.getBuildsCountByMetric(Metric.ip, emptyFilter) == [:]
        persistence.getBuildsCountByMetric(Metric.image, emptyFilter) == [:]
        persistence.getBuildsCountByMetric(Metric.user, emptyFilter) == [:]
        persistence.getPullsCountByMetric(Metric.ip, emptyFilter) == [:]
        persistence.getPullsCountByMetric(Metric.image, emptyFilter) == [:]
        persistence.getPullsCountByMetric(Metric.user, emptyFilter) == [:]
    }

    def 'should get zero for builds count, pulls count and distinct metric count if no record found' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)

        expect:
        def emptyFilter = new MetricFilter.Builder().build()
        persistence.getPullsCount(emptyFilter) == 0
        persistence.getBuildsCount(emptyFilter) == 0
        persistence.getDistinctMetrics(Metric.ip, emptyFilter) == 0
        persistence.getDistinctMetrics(Metric.image, emptyFilter) == 0
        persistence.getDistinctMetrics(Metric.user, emptyFilter) == 0
    }

    def 'should return builds count per metric with valid date when date filter is applied and date is missing in DB records' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)

        //record with no date
        def build4 = new WaveBuildRecord(
                buildId: 'test4',
                dockerFile: 'test4',
                condaFile: 'test4',
                targetImage: 'testImage4',
                userName: 'testUser4',
                userEmail: 'test4@xyz.com',
                userId: 4,
                requestIp: '127.0.0.4',
                duration: Duration.ofSeconds(1),
                exitStatus: 0)

        def build5 = new WaveBuildRecord(
                buildId: 'test5',
                dockerFile: 'test5',
                condaFile: 'test5',
                targetImage: 'testImage5',
                userName: 'testUser5',
                userEmail: 'test5@xyz.com',
                userId: 4,
                requestIp: '127.0.0.5',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0)
        and:
        persistence.saveBuild(build4)
        persistence.saveBuild(build5)
        sleep 200

        expect: 'ignore wave_build records with no startDate'
        def datesFilter = new MetricFilter.Builder().dates(Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()).build()
        persistence.getBuildsCountByMetric(Metric.ip, datesFilter) == [
                '127.0.0.5': 1
        ]
        persistence.getBuildsCountByMetric(Metric.user, datesFilter) == [
                'test5@xyz.com': 1
        ]
        persistence.getBuildsCountByMetric(Metric.image, datesFilter) == [
                'testImage5': 1
        ]
    }

    def 'should return pulls count per metric with valid date when date filter is applied and date is missing in DB records' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)

        //record with no timestamp
        def TOKEN4 = '1234abc'
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'],
                layers: [ new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/v2.2.8-amd64.json')])
        def req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'abc',
        )
        def wave = "wave.io/wt/$TOKEN4/hello-nf-world"
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def data = new ContainerRequestData(new PlatformId(user,100), 'hello-nf-world' )
        def addr = "100.200.300.404"
        def exp = Instant.now().plusSeconds(3600)
        def request4 = new WaveContainerRecord(req, data, wave, addr, exp)
        def timestamp = WaveContainerRecord.getDeclaredField("timestamp")
        timestamp.accessible = true
        timestamp.set(request4 , null)

        def TOKEN5 = '12345abc'
        cfg = new ContainerConfig(entrypoint: ['sh'])
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
        wave = "wave.io/wt/$TOKEN5/hello-fs-world"
        user = null
        data = new ContainerRequestData(new PlatformId(user,100), 'hello-fs-world' )
        addr = "100.200.300.405"
        exp = Instant.now().plusSeconds(3600)
        def request5 = new WaveContainerRecord(req, data, wave, addr, exp)

        and:
        persistence.saveContainerRequest(TOKEN4, request4)
        persistence.saveContainerRequest(TOKEN5, request5)
        sleep(200)

        expect: 'should ignore wave_request records with no timestamp'
        def datesFilter = new MetricFilter.Builder().dates(Instant.now().truncatedTo(ChronoUnit.DAYS), Instant.now()).build()
        persistence.getPullsCountByMetric(Metric.user, datesFilter) == [
                'anonymous': 1
        ]
        persistence.getPullsCountByMetric(Metric.ip, datesFilter) ==[
                '100.200.300.405': 1
        ]
        persistence.getPullsCountByMetric(Metric.image, datesFilter) == [
                'hello-fs-world': 1
        ]
    }

    def 'should get the correct where clause for wave_build table' () {
        expect:
        SurrealPersistenceService.getBuildMetricFilter(new MetricFilter.Builder().success(SUCCESS).dates(STARTDATE, ENDDATE).build()) == SURREALDBFILTER
        where:
        SUCCESS | STARTDATE     | ENDDATE           | SURREALDBFILTER
        null    | null          | null              | ''
        true    | null          | null              | 'WHERE exitStatus = 0'
        false   | null          | null              | 'WHERE exitStatus != 0'
        null    | Instant.now() | Instant.now()     | "WHERE type::is::datetime(startTime) AND type::datetime(startTime) >= '$STARTDATE' AND type::datetime(startTime) <= '$ENDDATE'"
        null    | null          | Instant.now()     | ''
        true    | null          | Instant.now()     | "WHERE exitStatus = 0"
        true    | Instant.now() | Instant.now()     | "WHERE type::is::datetime(startTime) AND type::datetime(startTime) >= '$STARTDATE' AND type::datetime(startTime) <= '$ENDDATE' AND exitStatus = 0"
        false   | Instant.now() | Instant.now()     | "WHERE type::is::datetime(startTime) AND type::datetime(startTime) >= '$STARTDATE' AND type::datetime(startTime) <= '$ENDDATE' AND exitStatus != 0"
    }

    def 'get the correct where clause for wave_request table' () {
        expect:
        SurrealPersistenceService.getPullMetricFilter(new MetricFilter.Builder().fusion(FUSION).dates(STARTDATE, ENDDATE).build()) == SURREALDBFILTER
        where:
        FUSION | STARTDATE     | ENDDATE            | SURREALDBFILTER
        null    | null          | null              | ''
        true    | null          | null              | 'WHERE fusionVersion != NONE'
        false   | null          | null              | 'WHERE fusionVersion = NONE'
        null    | Instant.now() | Instant.now()     | "WHERE type::is::datetime(timestamp) AND type::datetime(timestamp) >= '$STARTDATE' AND type::datetime(timestamp) <= '$ENDDATE'"
        null    | null          | Instant.now()     | ''
        true    | null          | Instant.now()     | 'WHERE fusionVersion != NONE'
        true    | Instant.now() | Instant.now()     | "WHERE type::is::datetime(timestamp) AND type::datetime(timestamp) >= '$STARTDATE' AND type::datetime(timestamp) <= '$ENDDATE' AND fusionVersion != NONE"
        false   | Instant.now() | Instant.now()     | "WHERE type::is::datetime(timestamp) AND type::datetime(timestamp) >= '$STARTDATE' AND type::datetime(timestamp) <= '$ENDDATE' AND fusionVersion = NONE"
    }
}
