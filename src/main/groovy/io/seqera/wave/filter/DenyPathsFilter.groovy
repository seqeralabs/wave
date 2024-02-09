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

package io.seqera.wave.filter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter;
import  io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

/**
 * Block any HTTP request whose target path is included in the {@code wave.denyPaths}
 * configuration attribute
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@Slf4j
@CompileStatic
@Filter("/**")
@Requires(property = 'wave.denyPaths')
class DenyPathsFilter implements HttpServerFilter {

    @Value('${wave.denyPaths}')
    private List<String> deniedPaths

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        // Check if the request path matches any of the ignored paths
        if (isDeniedPath(request.path, deniedPaths)) {
            // Return immediately without processing the request
            log.debug("Request denied: ${request}")
            return Flux.just(HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED))
        }
        // Continue processing the request
        return chain.proceed(request)
    }

    protected boolean isDeniedPath(String path, List<String> paths) {
        return paths.contains(path)
    }

    @Override
    int getOrder() {
        return FilterOrder.DENY_PATHS
    }
}

