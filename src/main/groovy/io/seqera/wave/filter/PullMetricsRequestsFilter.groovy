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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.service.metric.MetricsService
import jakarta.inject.Inject
import jakarta.inject.Named
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V1_JWS_TYPE
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V1_TYPE
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V2_TYPE
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1

/**
 * Implements a filter to check whether a request is pull request or not
 * and then increment the metrics counters
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Filter(value = '/v2/**', methods = HttpMethod.GET)
@Requires(property = 'wave.metrics.enabled', value = 'true')
class PullMetricsRequestsFilter implements HttpServerFilter {

    private static final List<String> MANIFEST_TYPES = List.of(
            DOCKER_MANIFEST_V2_TYPE,
            OCI_IMAGE_MANIFEST_V1,
            DOCKER_MANIFEST_V1_JWS_TYPE,
            DOCKER_MANIFEST_V1_TYPE
    )

    @Inject
    private MetricsService metricsService

    @Inject
    private RouteHandler routeHelper

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Flux.from(chain.proceed(request))
                .doOnNext(response -> {
                        incMetricsCounters(request, response)
                })
    }

    protected void incMetricsCounters(HttpRequest request, HttpResponse response) {
        final contentType = response.headers.get(HttpHeaders.CONTENT_TYPE)
        if( contentType && contentType in MANIFEST_TYPES ) {
            final route = routeHelper.parse(request.path)
            CompletableFuture.supplyAsync(() -> metricsService.incrementPullsCounter(route.identity), executor)
            final version = route.request?.containerConfig?.fusionVersion()
            if (version) {
                CompletableFuture.supplyAsync(() -> metricsService.incrementFusionPullsCounter(route.identity), executor)
            }
        }
    }

    @Override
    int getOrder() {
       FilterOrder.PULL_METRICS
    }
}
