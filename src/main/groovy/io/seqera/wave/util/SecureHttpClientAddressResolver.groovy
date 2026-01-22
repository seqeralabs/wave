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

package io.seqera.wave.util

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.util.DefaultHttpClientAddressResolver
import jakarta.inject.Singleton

/**
 * Secure implementation that takes the RIGHTMOST IP from X-Forwarded-For
 * to prevent IP spoofing attacks when behind AWS ALB.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
@Replaces(DefaultHttpClientAddressResolver)
@Requires(property = 'micronaut.server.host-resolution.client-address-header')
class SecureHttpClientAddressResolver extends DefaultHttpClientAddressResolver {

    SecureHttpClientAddressResolver(HttpServerConfiguration serverConfiguration) {
        super(serverConfiguration)
        log.info "Secure HTTP client address resolver enabled - uses rightmost IP from X-Forwarded-For"
    }

    @Override
    String resolve(HttpRequest request) {
        // Read X-Forwarded-For header directly from the request
        // We cannot use super.resolve() because it returns the FIRST IP, not the full list
        final String headerValue = request.getHeaders().get('X-Forwarded-For')

        if (!headerValue) {
            // Header not present, use socket address
            final String socketIp = super.resolve(request)
            if (log.isTraceEnabled()) {
                log.trace "No X-Forwarded-For header, using socket address: '$socketIp'"
            }
            return socketIp
        }

        if (!headerValue.contains(',')) {
            // Single IP in header, return as is
            final String singleIp = headerValue.trim()
            if (log.isTraceEnabled()) {
                log.trace "Single IP in X-Forwarded-For: '$singleIp'"
            }
            return singleIp
        }

        // SECURITY FIX: Multiple IPs (comma-separated) - take the RIGHTMOST one
        // When behind ALB: X-Forwarded-For: <spoofed-ip>, <real-client-ip>
        // The rightmost IP is added by ALB and is trustworthy
        final String[] ips = headerValue.split(',')
        final String rightmostIp = ips[ips.length - 1].trim()

        log.info "Multiple IPs in X-Forwarded-For: '$headerValue' -> using rightmost: '$rightmostIp'"

        return rightmostIp
    }
}
