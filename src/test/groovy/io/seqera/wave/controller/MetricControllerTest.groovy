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
class MetricControllerTest extends Specification {

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
        def data = new ContainerRequestData(1, 100, 'hello-world')
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
        data = new ContainerRequestData(1, 100, 'hello-world')
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
        data = new ContainerRequestData(1, 100, 'hello-wave-world')
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

    def'should get 401 when no credentials provided'() {
        when:
        def req = HttpRequest.GET("$PREFIX/build/ip")
        def res = client.toBlocking().exchange(req, Map)
        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 401
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
        given: "Date is tomorrow"
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
        res.body() == ['testUser1': 1, 'unknown': 1]
        res.status.code == 200
    }

    def "should get the correct build count per metrics between dates and status 200"() {
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
        res.body() == ['testImage2': 1, 'testImage1': 1]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/build/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['testUser1': 1, 'unknown': 1]
        res.status.code == 200
    }

    def 'should return the total build count and status 200'() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when: 'no filter provided'
        def req = HttpRequest.GET("$PREFIX/build/count").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'return total build count'
        res.body() == ['count': 3]
        res.status.code == 200

        when: 'dates are provided'
        req = HttpRequest.GET("$PREFIX/build/count?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'return total build count between provided dates'
        res.body() == ['count': 2]
        res.status.code == 200

        when: 'success query parameter is set to true'
        req = HttpRequest.GET("$PREFIX/build/count?success=true").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'return total build count between provided dates'
        res.body() == ['count': 2]
        res.status.code == 200

    }

    def 'should return zero total build count and status 200 when no record found' () {
        given: "Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/build/count?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count': 0]
        res.status.code == 200
    }

    def 'should return the correct pull counts per metric and status 200' () {
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

    def "should get the pull count per metric between dates and status 200"() {
        given:
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

    def "should get the total pull count and status 200"() {
        given:
        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when:
        def req = HttpRequest.GET("$PREFIX/pull/count").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':3]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/pull/count?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count': 2]
        res.status.code == 200
    }

    def 'should return zero total pull count and status 200 when no record found' () {
        given: "Date is tomorrow"
        def date = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/pull/count?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count': 0]
        res.status.code == 200
    }

    def 'should return the distinct count per metric and status 200' () {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/ip").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/image").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':2]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/user").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':1]
        res.status.code == 200
    }

    def 'should return the distinct count per metric between provided dates and status 200' () {
        given:
        def TOKEN = '1236abc'
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
        def reqt = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'xyz',
                timestamp: Instant.now().toString()
        )
        def data = new ContainerRequestData( 1, 100, 'hello-nf-world' )
        def wave = "wave.io/wt/$TOKEN/hello-nf-world"
        def user = new User(id: 3, userName: 'test', email: 'test@gmail.com')
        def addr = "100.200.300.402"
        def exp = Instant.now().plusSeconds(3600)
        def request = new WaveContainerRecord(reqt, data, wave, user, addr, exp)

        and:
        persistenceService.saveContainerRequest(TOKEN, request)

        def date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        when:
        def req = HttpRequest.GET("$PREFIX/distinct/ip?startDate=$date&endDate=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':3]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/image?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':3]
        res.status.code == 200

        when:
        req = HttpRequest.GET("$PREFIX/distinct/user?startDate=$date&endDate=$date").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then:
        res.body() == ['count':2]
        res.status.code == 200
    }

    def 'return correct message when date format is not valid' () {
        when:
        def req = HttpRequest.GET("$PREFIX/distinct/user?startDate=2024-02-07&endDate=2024-02-0").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 400
        e.message == 'Date format should be yyyy-mm-dd'
    }

    def 'startDate and endDate should Cover the last day' () {
        given:
        def starDate ='2024-02-07'
        def endDate = '2024-02-07'

        expect:'1 day difference'
        Duration.between(MetricController.parseStartDate(starDate), MetricController.parseEndDate(endDate)).toString() == 'PT23H59M59.999999999S'
    }
}
