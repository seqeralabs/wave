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

package io.seqera.wave.filter

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.internal.AtomicRateLimiter
import io.github.resilience4j.ratelimiter.internal.AtomicRateLimiter.AtomicRateLimiterMetrics
import io.micronaut.cache.SyncCache
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.seqera.wave.exchange.ErrorResponse
import io.seqera.wave.exchange.RegistryErrorResponse
import jakarta.inject.Named
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
/**
 * Implements an API rate limiter
 *
 * Enable it adding in the `application.yml` the following snippet
 *
 * rate-limiter:
 *   timeout-duration: 100ms
 *   limit-refresh-period: 1s
 *   limit-for-period: 3
 *
 * https://www.joyk.com/dig/detail/1550242906768140
 * https://medium.com/@storozhuk.b.m/rate-limiter-internals-in-resilience4j-48776e433b90
 * https://www.baeldung.com/resilience4j
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Filter("/**")
@Requires(property = 'rate-limit.httpRequest')
@Context
class RateLimiterFilter implements HttpServerFilter {

    private final RateLimiterConfig config

    private final SyncCache<AtomicRateLimiter> limiters

    /**
     * Creates the rate limiter with the provided config
     *
     * @param limiters
     * @param opts
     */
    RateLimiterFilter(@Named("rate-limiter") SyncCache limiters, RateLimiterOptions opts) {
        log.info "API rate-limiter filter: limitRefreshPeriod=$opts.limitRefreshPeriod; limitForPeriod=$opts.limitForPeriod; timeoutDuration=$opts.timeoutDuration; statusCode=$opts.statusCode"
        opts.validate()

        this.limiters = limiters
        this.config = RateLimiterConfig.custom()
                .limitRefreshPeriod(opts.limitRefreshPeriod)
                .limitForPeriod(opts.limitForPeriod)
                .timeoutDuration(opts.timeoutDuration)
                .build()

    }

    @Override
    int getOrder() {
        return FilterOrder.RATE_LIMITER
    }

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return limit0(request,chain)
    }

    private Publisher<MutableHttpResponse<?>> limit0(HttpRequest<?> request, ServerFilterChain chain) {
        if( request.path.startsWith('/ping') ) {
            // ignore rate limit for ping endpoint
            return chain.proceed(request)
        }

        final key = getKey(request);
        final limiter = getLimiter(key);

        if (limiter.acquirePermission()) {
            log.trace "Rate OK for key: $key"
            return chain.proceed(request)
        }
        else {
            final metrics = limiter.getDetailedMetrics()
            // return a different status depending the request source
            // requests starting with /v2 are originated by docker which is expecting 429 error code (too many request)
            // other requests are originated by nextflow with is expecting 503 error code (slow down)
            final httpStatus = request.path.startsWith('/v2') ? 429 : 503
            log.warn "Too many request for IP: ${key}; request: $request - Wait ${TimeUnit.NANOSECONDS.toMillis(metrics.getNanosToWait())} millis issuing a new request"
            return createOverLimitResponse(metrics, httpStatus)
        }
    }

    private String getKey(HttpRequest<?> request) {
        final address = request.getHeaders().get('X-Forwarded-For') ?: request.getRemoteAddress().getAddress().getHostAddress()
        if( log.isTraceEnabled() ) {
            log.trace "Filter request\n- uri: ${request.getUri()}\n- address: ${address}\n- headers: ${request.getHeaders().asMap()}"
        }
        return address
    }

    private AtomicRateLimiter getLimiter(String key) {
        return limiters.get(key, AtomicRateLimiter, ()-> new AtomicRateLimiter(key, config) );
    }

    private Publisher<MutableHttpResponse<?>> createOverLimitResponse(AtomicRateLimiterMetrics metrics, int statusCode) {
        final secondsToWait = TimeUnit.NANOSECONDS.toSeconds(metrics.getNanosToWait())
        final String msg = "Maximum request rate exceeded - Wait ${secondsToWait}secs before issuing a new request"
        // see
        // https://distribution.github.io/distribution/spec/api/#on-failure-too-many-requests-1
        final body = statusCode==429
                ? new RegistryErrorResponse('TOOMANYREQUESTS', msg)
                : new ErrorResponse(msg)
        final resp = HttpResponse
                        .status(HttpStatus.valueOf(statusCode))
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(secondsToWait))
                        .body(body)
        return Flux.just(resp)
    }

}
