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

package io.seqera.wave.configuration

import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 * Configuration for HTTP security headers
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@ToString(includeNames = true, includePackage = false)
@CompileStatic
@Slf4j
@Singleton
@Requires(property = 'wave.security.headers.enabled', value = 'true', defaultValue = 'true')
class SecurityHeadersConfig {

    /**
     * Enable or disable security headers globally
     */
    @Value('${wave.security.headers.enabled:true}')
    Boolean enabled

    /**
     * HSTS max-age in seconds
     */
    @Value('${wave.security.headers.hsts.max-age:31536000}')
    Long hstsMaxAge

    /**
     * Include subdomains in HSTS
     */
    @Value('${wave.security.headers.hsts.include-sub-domains:true}')
    Boolean hstsIncludeSubDomains

    /**
     * X-Frame-Options header value
     */
    @Nullable
    @Value('${wave.security.headers.frame-options:DENY}')
    String frameOptions

    /**
     * X-Content-Type-Options header value
     */
    @Nullable
    @Value('${wave.security.headers.content-type-options:nosniff}')
    String contentTypeOptions

    /**
     * Referrer-Policy header value
     */
    @Nullable
    @Value('${wave.security.headers.referrer-policy:strict-origin-when-cross-origin}')
    String referrerPolicy

    /**
     * Permissions-Policy header value
     */
    @Nullable
    @Value('${wave.security.headers.permissions-policy:camera=(), microphone=(), geolocation=()}')
    String permissionsPolicy

    /**
     * Content-Security-Policy header value
     */
    @Nullable
    @Value('${wave.security.headers.csp:default-src \'self\'; frame-ancestors \'none\'}')
    String csp

    @PostConstruct
    private void init() {
        log.info("Security headers config: enabled=${enabled}; hsts-max-age=${hstsMaxAge}; hsts-include-sub-domains=${hstsIncludeSubDomains}; " +
                "frame-options=${frameOptions}; content-type-options=${contentTypeOptions}; referrer-policy=${referrerPolicy}; " +
                "permissions-policy=${permissionsPolicy}; csp=${csp}")
    }

    /**
     * Build the HSTS header value
     */
    String getHstsValue() {
        if (hstsMaxAge == null) {
            return null
        }
        final result = new StringBuilder("max-age=${hstsMaxAge}")
        if (hstsIncludeSubDomains) {
            result.append("; includeSubDomains")
        }
        return result.toString()
    }
}