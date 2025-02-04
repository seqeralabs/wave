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

package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.metrics.enabled', value = 'true')
class MetricsControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    @Shared
    MetricsService metricsService

    final dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    final def date = LocalDate.now().format(dateFormatter)

    def setupSpec() {
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)
        metricsService.incrementBuildsCounter(null, 'arm64')
        metricsService.incrementBuildsCounter(platformId2, null)

        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)
        metricsService.incrementPullsCounter(null, 'arm64')
        metricsService.incrementPullsCounter(platformId2, null)

        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)
        metricsService.incrementFusionPullsCounter(null, 'arm64')
        metricsService.incrementFusionPullsCounter(platformId2, null)

        metricsService.incrementScansCounter(platformId1, 'amd64')
        metricsService.incrementScansCounter(platformId2, 'arm64')
        metricsService.incrementScansCounter(null, null)
        metricsService.incrementScansCounter(null, 'arm64')
        metricsService.incrementScansCounter(platformId2, null)

        metricsService.incrementMirrorsCounter(platformId1, 'amd64')
        metricsService.incrementMirrorsCounter(platformId2, 'arm64')
        metricsService.incrementMirrorsCounter(null, null)
        metricsService.incrementMirrorsCounter(null, 'arm64')
        metricsService.incrementMirrorsCounter(platformId2, null)
    }

    def 'should get http status code 401 when no credentials provided'() {
        when:
        def req = HttpRequest.GET("/v1alpha2/metrics/builds")
        def res = client.toBlocking().exchange(req, Map)
        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    def 'should get the correct builds count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/builds?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/builds?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/builds?org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/builds").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'builds', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def 'should get the correct pulls count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'pulls', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'pulls', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/pulls?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'pulls', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/pulls").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'pulls', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

    }

    def 'should get the correct fusion pulls count and http status code 200'() {
        when:'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'fusion', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'fusion', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'fusion', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'fusion', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def 'should get the correct scans count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/scans?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'scans', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/scans?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'scans', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/scans?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'scans', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/scans").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'scans', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def 'should get the correct mirrors count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/mirrors?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'mirrors', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/mirrors?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'mirrors', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/mirrors?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'mirrors', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/mirrors").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'mirrors', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def 'should validate query parameters'() {
        when: 'wrong date format is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=2024-03-2").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then: 'should get 400 response code and message'
        def e = thrown(HttpClientResponseException)
        e.message == 'date format should be yyyy-MM-dd'
        e.status.code == 400
    }

    def '[v1alpha3] should get the correct builds count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha3/metrics/builds?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha3/metrics/builds?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha3/metrics/builds?org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha3/metrics/builds?org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'builds', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha3/metrics/builds").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'builds', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def '[v1alpha3] should get the correct pulls count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'pulls', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'pulls', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/pulls?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'pulls', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/pulls").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'pulls', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

    }

    def '[v1alpha3] should get the correct fusion pulls count and http status code 200'() {
        when:'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'fusion', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'fusion', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'fusion', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/fusion/pulls").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'fusion', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def '[v1alpha3] should get the correct scans count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/scans?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'scans', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/scans?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'scans', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/scans?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'scans', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/scans").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'scans', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def '[v1alpha3] should get the correct mirrors count and http status code 200'() {
        when: 'only date is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/mirrors?date=$date").basicAuth("username", "password")
        def res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'mirrors', count:5, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200

        when: 'date and org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/mirrors?date=$date&org=org1.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'mirrors', count:1, orgs:['org1.com': 1]]
        res.status.code == 200

        when: 'only org is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/mirrors?org=org2.com").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct count'
        res.body() == [metric:'mirrors', count:2, orgs:['org2.com': 2]]
        res.status.code == 200

        when: 'no param is provided'
        req = HttpRequest.GET("/v1alpha2/metrics/mirrors").basicAuth("username", "password")
        res = client.toBlocking().exchange(req, Map)

        then: 'should get the correct org count'
        res.body() == [metric:'mirrors', count:3, orgs:['org1.com': 1, 'org2.com': 2]]
        res.status.code == 200
    }

    def '[v1alpha3] should validate query parameters'() {
        when: 'wrong date format is provided'
        def req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=2024-03-2").basicAuth("username", "password")
        client.toBlocking().exchange(req, Map)

        then: 'should get 400 response code and message'
        def e = thrown(HttpClientResponseException)
        e.message == 'date format should be yyyy-MM-dd'
        e.status.code == 400
    }
}
