/*
 * Copyright (c) 2019-2020, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
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

    public final static int ORDER = -50;

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
        return ORDER
    }

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return limit0(request,chain)
    }

    private Publisher<MutableHttpResponse<?>> limit0(HttpRequest<?> request, ServerFilterChain chain) {
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

        final message = "Maximum request rate exceeded - Wait ${secondsToWait}secs before issuing a new request"
        final body = new ErrorResponse(message)
        final resp = HttpResponse
                        .status(HttpStatus.valueOf(statusCode))
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(secondsToWait))
                        .body(body)
        return Flux.just(resp)
    }

}
