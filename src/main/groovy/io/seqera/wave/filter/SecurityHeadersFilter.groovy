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
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.seqera.wave.configuration.SecurityHeadersConfig
import jakarta.inject.Inject
import static io.micronaut.http.annotation.ServerFilter.MATCH_ALL_PATTERN

/**
 * HTTP filter to add security headers to all responses
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
@ServerFilter(MATCH_ALL_PATTERN)
@Requires(property = 'wave.security.headers.enabled', value = 'true', defaultValue = 'true')
class SecurityHeadersFilter implements Ordered {

    @Inject
    private SecurityHeadersConfig config

    @Override
    int getOrder() {
        return FilterOrder.SECURITY_HEADERS
    }

    @ResponseFilter
    void responseFilter(HttpResponse<?> response) {
        if (response instanceof MutableHttpResponse) {
            addSecurityHeaders((MutableHttpResponse<?>) response)
        }
    }

    /**
     * Add security headers to the HTTP response
     *
     * @param response The mutable HTTP response to add headers to
     */
    protected void addSecurityHeaders(MutableHttpResponse<?> response) {
        // Add HSTS header
        final hstsValue = config.getHstsValue()
        if (hstsValue) {
            response.header('Strict-Transport-Security', hstsValue)
        }

        // Add X-Frame-Options
        if (config.frameOptions) {
            response.header('X-Frame-Options', config.frameOptions)
        }

        // Add X-Content-Type-Options
        if (config.contentTypeOptions) {
            response.header('X-Content-Type-Options', config.contentTypeOptions)
        }

        // Add Referrer-Policy
        if (config.referrerPolicy) {
            response.header('Referrer-Policy', config.referrerPolicy)
        }

        // Add Permissions-Policy
        if (config.permissionsPolicy) {
            response.header('Permissions-Policy', config.permissionsPolicy)
        }

        // Add Content-Security-Policy
        if (config.contentSecurityPolicy) {
            response.header('Content-Security-Policy', config.contentSecurityPolicy)
        }

        log.trace "Added security headers to response"
    }
}
