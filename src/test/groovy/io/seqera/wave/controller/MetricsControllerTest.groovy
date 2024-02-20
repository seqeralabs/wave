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
package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@Property(name = 'wave.metrics.enabled', value = 'true')
@MicronautTest
class MetricsControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    final PREFIX = '/v1alpha1/metrics'

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
                exitStatus: 1)

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
                exitStatus: 0)

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
                exitStatus: 0)

        and:
        persistenceService.saveBuild(build1)
        persistenceService.saveBuild(build2)
        persistenceService.saveBuild(build3)

        //add some container records
        def TOKEN1 = '123abc'
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'],
                layers: [new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/v2.2.8-amd64.json')])
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
        def data = new ContainerRequestData(new PlatformId(user, 100), 'hello-world')
        def addr = "100.200.300.400"
        def exp = Instant.now().plusSeconds(3600)
        def request1 = new WaveContainerRecord(req, data, wave, addr, exp)

        def TOKEN2 = '1234abc'
        cfg = new ContainerConfig(entrypoint: ['/opt/fusion'],
                layers: [new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/v2.2.8-amd64.json')])
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
        user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        data = new ContainerRequestData(new PlatformId(user, 100), 'hello-world')
        addr = "100.200.300.400"
        exp = Instant.now().plusSeconds(3600)
        def request2 = new WaveContainerRecord(req, data, wave, addr, exp)

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
        wave = "wave.io/wt/$TOKEN3/hello-wave-world"
        user = null
        data = new ContainerRequestData(new PlatformId(user, 100), 'hello-wave-world')
        addr = "100.200.300.401"
        exp = Instant.now().plusSeconds(3600)
        def request3 = new WaveContainerRecord(req, data, wave, addr, exp)

        persistenceService.saveContainerRequest(TOKEN1, request1)
        persistenceService.saveContainerRequest(TOKEN2, request2)
        persistenceService.saveContainerRequest(TOKEN3, request3)
        sleep 300
    }

    def 'should get 401 when no credentials provided'() {
        when:
        def req = HttpRequest.GET("$PREFIX/builds/ip")
        def res = client.toBlocking().exchange(req, Map)
        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    def "should get the correct build count per metrics and status 200"() {
        when:
        def req = HttpRequest.GET("$PREFIX/builds/ip").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['127.0.0.1': 2, '127.0.0.2': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/image").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['testImage1': 2, 'testImage2': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/user").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['test1@xyz.com': 2, 'unknown': 1]]
        res.status.code == 200
    }

    def "should return empty body and status 200 when no build records found"() {
        given: "Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/builds/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [:]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [:]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [:]
        res.status.code == 200
    }

    def "should get the correct successful build count per metrics and status 200"() {
        when:
        def req = HttpRequest.GET("$PREFIX/builds/ip?success=true").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['127.0.0.2': 1, '127.0.0.1': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/image?success=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['testImage2': 1, 'testImage1': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/user?success=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['test1@xyz.com': 1, 'unknown': 1]]
        res.status.code == 200
    }

    def "should get the correct build count per metrics between dates and status 200"() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/builds/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result: ['127.0.0.1': 1, '127.0.0.2': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result: ['testImage2': 1, 'testImage1': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['test1@xyz.com': 1, 'unknown': 1]]
        res.status.code == 200
    }

    def "should get the correct build count per metrics in limit and status 200"() {
        when:
        def req = HttpRequest.GET("$PREFIX/builds/ip?limit=1").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['127.0.0.1': 2]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/image?limit=1").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['testImage1': 2]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/builds/user?limit=1").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['test1@xyz.com': 2]]
        res.status.code == 200
    }

    def 'should return the total build count and status 200'() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when: 'no filter provided'
        def req = HttpRequest.GET("$PREFIX/builds").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'return total build count'
        res.body() == [count: 3]
        res.status.code == 200

        when: 'dates are provided'
        req = HttpRequest.GET("$PREFIX/builds?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'return total build count between provided dates'
        res.body() == [count: 2]
        res.status.code == 200

        when: 'success query parameter is set to true'
        req = HttpRequest.GET("$PREFIX/builds?success=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'return total build count between provided dates'
        res.body() == [count: 2]
        res.status.code == 200

    }

    def 'should return zero total build count and status 200 when no record found'() {
        given: "Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/builds?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 0]
        res.status.code == 200
    }

    def 'should return the correct pull counts per metric and status 200'() {
        when:
        def req = HttpRequest.GET("$PREFIX/pulls/ip").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['100.200.300.401': 1, '100.200.300.400': 2]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/image").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['hello-world': 2, 'hello-wave-world': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/user").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['foo@gmail.com': 2, 'unknown': 1]]
        res.status.code == 200
    }

    def "should return null and status 200 when no pull records found"() {
        given: "Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/pulls/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [:]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [:]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [:]
        res.status.code == 200
    }

    def "should get the pull count per metric between dates and status 200"() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/pulls/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['100.200.300.401': 1, '100.200.300.400': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['hello-world': 1, 'hello-wave-world': 1]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['foo@gmail.com': 1, 'unknown': 1]]
        res.status.code == 200
    }

    def 'should return the correct pull counts per metric in limit and status 200'() {
        when:
        def req = HttpRequest.GET("$PREFIX/pulls/ip?limit=1").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['100.200.300.400': 2]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/image?limit=1").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['hello-world': 2]]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls/user?limit=1").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [result:['foo@gmail.com': 2]]
        res.status.code == 200
    }

    def "should get the total pull count and status 200"() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/pulls").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 3]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 2]
        res.status.code == 200
    }

    def "should get the total pull count for containers with and without fusion and status 200"() {
        when:
        def req = HttpRequest.GET("$PREFIX/pulls?fusion=false").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pulls?fusion=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 2]
        res.status.code == 200
    }

    def 'should return zero total pull count and status 200 when no record found'() {
        given: "Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/pulls?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 0]
        res.status.code == 200
    }

    def 'should return the distinct count per metric and status 200'() {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/ip").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/image").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/user").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200
    }

    def 'should return the distinct count per metric between provided dates and status 200'() {
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/distinct/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200
    }

    def 'should return the distinct count per metric with fusion and status 200'() {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/ip?fusion=true").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/image?fusion=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/user?fusion=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200
    }

    def 'should return the distinct count per metric without fusion and status 200'() {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/ip?fusion=false").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/image?fusion=false").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/user?fusion=false").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 0]
        res.status.code == 200
    }

    def 'return 400 status and message when date format is not valid'() {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/user?startDate=2024-02-07&endDate=2024-02-0").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 400
        e.message == 'Date format should be yyyy-mm-dd'
    }

    def 'return 400 status code and message metric in path is not valid'() {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/userId").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 400
        e.message == 'you have provided an invalid metric. The valid metrics are: ip, user, image'
    }

    def 'startDate and endDate should Cover the last day'() {
        given:
        def starDate = '2024-02-07'
        def endDate = '2024-02-07'

        expect: '1 day difference'
        Duration.between(MetricsController.parseStartDate(starDate), MetricsController.parseEndDate(endDate)).toString() == 'PT23H59M59.999999999S'
    }
}
