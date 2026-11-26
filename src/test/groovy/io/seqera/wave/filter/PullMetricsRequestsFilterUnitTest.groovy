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

package io.seqera.wave.filter

import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContentType
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
/**
 * Test the counter logic of {@link PullMetricsRequestsFilter} in isolation. The filter is also exercised
 * over HTTP by {@link PullMetricsRequestsFilterTest}, which needs an application context.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class PullMetricsRequestsFilterUnitTest extends Specification {

    static final String DIGEST = 'sha256:e2fc4e5012d16e7fe466f5291c476431beaa1f9b90a5c2125b493ed28e2aba57'

    def metricsService = Mock(MetricsService)
    def routeHandler = Mock(RouteHandler)
    def executor = Executors.newSingleThreadExecutor()
    def filter = new PullMetricsRequestsFilter(metricsService: metricsService, routeHelper: routeHandler, executor: executor)

    /** the counters are incremented asynchronously - wait for them before verifying */
    private void drain() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    static HttpResponse manifestResponse(String type=ContentType.DOCKER_MANIFEST_V2_TYPE) {
        HttpResponse.ok().header(HttpHeaders.CONTENT_TYPE, type)
    }

    def 'should count a pull when the route carries no wave token'() {
        given: 'a plain proxy pull: no token, therefore no container request'
        def route = RoutePath.v2path('manifests', 'docker.io', 'library/ubuntu', DIGEST)

        when:
        filter.incMetricsCounters(HttpRequest.GET("/v2/docker.io/library/ubuntu/manifests/$DIGEST"), manifestResponse())
        drain()

        then:
        1 * routeHandler.parse(_) >> route
        and: 'the pull is counted with no architecture, instead of throwing'
        1 * metricsService.incrementPullsCounter(PlatformId.NULL, null)
        and:
        noExceptionThrown()
    }

    def 'should count a pull with the architecture of the container request'() {
        given:
        def request = ContainerRequest.of(containerImage: 'docker.io/library/ubuntu:latest', platform: ContainerPlatform.of('linux/amd64'))
        def route = RoutePath.v2path('manifests', 'docker.io', 'library/ubuntu', DIGEST, request, 'tok123')

        when:
        filter.incMetricsCounters(HttpRequest.GET("/v2/wt/tok123/library/ubuntu/manifests/$DIGEST"), manifestResponse())
        drain()

        then:
        1 * routeHandler.parse(_) >> route
        and:
        1 * metricsService.incrementPullsCounter(_, 'amd64')
    }

    def 'should ignore a response that is not a container manifest'() {
        when: 'an image index is not a manifest type, nor is a blob'
        filter.incMetricsCounters(HttpRequest.GET("/v2/docker.io/library/ubuntu/manifests/latest"), manifestResponse(ContentType.OCI_IMAGE_INDEX_V1))

        then:
        0 * routeHandler.parse(_)
        0 * metricsService.incrementPullsCounter(_, _)
    }
}
