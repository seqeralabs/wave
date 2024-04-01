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

package io.seqera.wave.filter;

import java.time.Duration;

import groovy.transform.CompileStatic;
import groovy.util.logging.Slf4j;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
/**
 * Implements an filter that log as warning http requests taking more
 * that a specified threshold duration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property ="wave.trace.slow-endpoint")
@CompileStatic
@Filter("/**")
class TraceSlowEndpointFilter implements HttpServerFilter {

    @Value("${wave.trace.slow-endpoint.duration:1m}")
    private Duration duration;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        final long begin = System.currentTimeMillis();
        final Publisher<MutableHttpResponse<?>> publisher = chain.proceed(request);
        return new TraceSlowEndpointPublisher<MutableHttpResponse<?>>(publisher,request,begin,duration);
    }

}
