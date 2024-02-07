package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.tower.User
import jakarta.inject.Inject

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Requires(property = 'wave.auth.basic.username', value = 'username')
@Requires(property = 'wave.auth.basic.password', value = 'password')
class MetricControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject PersistenceService persistenceService

    final PREFIX='/v1alpha1/metrics'

    def setup() {
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
        persistenceService.saveBuild(build1)
        persistenceService.saveBuild(build2)
        persistenceService.saveBuild(build3)

        //add some container records
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

        persistenceService.saveContainerRequest(TOKEN1, request1)
        persistenceService.saveContainerRequest(TOKEN2, request2)
        persistenceService.saveContainerRequest(TOKEN3, request3)
        sleep 300
    }

    def "should get the correct build count per metrics and status 200"() {
        when:
        def req = HttpRequest.GET("$PREFIX/build/ip").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['127.0.0.1': 2, '127.0.0.2': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/image").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['testImage1': 2, 'testImage2': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/user").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['testUser1': 2, 'unknown': 1]
        res.status.code == 200
    }
    def "should return null and status 404 when no build records found"() {
        given:"Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/build/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 404

        when:
        req = HttpRequest.GET("$PREFIX/build/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        e = thrown(HttpClientResponseException)
        e.status.code == 404

        when:
        req = HttpRequest.GET("$PREFIX/build/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        e = thrown(HttpClientResponseException)
        e.status.code == 404
    }

    def "should get the correct successful build count per metrics and status 200"() {
        when:
        def req = HttpRequest.GET("$PREFIX/build/ip?success=true").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['127.0.0.2': 1, '127.0.0.1': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/image?success=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['testImage2': 1, 'testImage1': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/user?success=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() ==   ['testUser1': 1, 'unknown': 1]
        res.status.code == 200
    }

    def "should get the correct count per metrics between dates and status 200"() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/build/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['127.0.0.1': 1, '127.0.0.2': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() ==['testImage2': 1, 'testImage1': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['testUser1': 1, 'unknown': 1]
        res.status.code == 200
    }

    def 'should return the correct pull counts per metrics and status 200' () {
        when:
        def req = HttpRequest.GET("$PREFIX/pull/ip").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['100.200.300.401':1, '100.200.300.400':2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pull/image").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['hello-world': 2, 'hello-wave-world': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pull/user").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['foo': 2, 'unknown': 1]
        res.status.code == 200
    }

    def "should return null and status 404 when no pull records found"() {
        given:"Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/pull/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 404

        when:
        req = HttpRequest.GET("$PREFIX/pull/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        e = thrown(HttpClientResponseException)
        e.status.code == 404

        when:
        req = HttpRequest.GET("$PREFIX/pull/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        e = thrown(HttpClientResponseException)
        e.status.code == 404
    }

    def "should get the records between dates and status 200"() {
        given:"Date is tomorrow"
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/pull/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['100.200.300.401':1, '100.200.300.400':1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pull/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['hello-world': 1, 'hello-wave-world': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pull/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['foo': 1, 'unknown': 1]
        res.status.code == 200
    }
}
