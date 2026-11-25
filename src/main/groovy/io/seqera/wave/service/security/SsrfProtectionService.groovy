/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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

package io.seqera.wave.service.security

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.seqera.util.net.SsrfValidationException
import io.seqera.util.net.SsrfValidator
import io.seqera.wave.configuration.SsrfConfig
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Centralise SSRF protection checks for user-supplied container registry names.
 *
 * All checks are no-ops unless {@code wave.security.ssrf-protection.enabled} is set
 * to {@code true}, so existing deployments are unaffected until they opt in.
 *
 * @author Oussama El Azizi <oussama.elazizi@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class SsrfProtectionService {

    @Inject
    SsrfConfig config

    boolean isEnabled() {
        return config.ssrfProtectionEnabled
    }

    /**
     * Validate a registry endpoint before any connection is opened to it.
     *
     * @param endpoint The registry endpoint URI as resolved by the lookup service
     * @throws BadRequestException when the endpoint host is a loopback, link-local,
     *      private or metadata address, or resolves to one
     */
    void checkEndpoint(URI endpoint) {
        checkHost(endpoint?.getHost())
    }

    /**
     * Validate a registry host name or IP address. The validation resolves the name
     * and rejects loopback, link-local, private (RFC 1918) and cloud metadata addresses.
     *
     * @param host The registry host name, without scheme or port
     * @throws BadRequestException when the host is not allowed
     */
    void checkHost(String host) {
        if( !enabled )
            return
        try {
            SsrfValidator.validateHost(host)
        }
        catch (SsrfValidationException e) {
            log.warn "SSRF protection rejected registry host '${host}' - reason: ${e.message}"
            throw new BadRequestException(e.message)
        }
    }

    /**
     * Enforce the registry allowlist for anonymous requests. Authenticated requests
     * (any non-null, non-empty {@link PlatformId}) may reference any registry; they are
     * still subject to {@link #checkHost} when the lookup is performed.
     *
     * @param registry The registry name referenced by the request e.g. {@code quay.io}
     * @param identity The platform identity of the caller; {@code null} or {@link PlatformId#NULL} means anonymous
     * @throws BadRequestException when the caller is anonymous and the registry is not in the allowlist
     */
    void checkAnonymousRegistryAccess(String registry, @Nullable PlatformId identity) {
        if( !enabled )
            return
        if( identity )
            return
        final allowlist = config.registryAllowlist
        if( !allowlist )
            return
        if( !isAllowed(registry, allowlist) ) {
            log.warn "SSRF protection rejected anonymous access to registry '${registry}' - allowed registries: ${allowlist}"
            throw new BadRequestException("Container registry '${registry}' is not allowed for anonymous requests - authenticate with Seqera Platform to access other registries")
        }
    }

    static protected boolean isAllowed(String registry, List<String> allowlist) {
        if( !registry )
            return false
        final host = stripPort(registry.toLowerCase())
        for( String entry : allowlist ) {
            final pattern = stripPort(entry?.trim()?.toLowerCase())
            if( !pattern )
                continue
            if( pattern.startsWith('*.') ) {
                // '*.example.com' matches any subdomain and the bare domain itself
                if( host.endsWith(pattern.substring(1)) || host==pattern.substring(2) )
                    return true
            }
            else if( host==pattern )
                return true
        }
        return false
    }

    static private String stripPort(String host) {
        if( !host )
            return host
        final p = host.lastIndexOf(':')
        // only strip when the suffix is a port number, to not mangle IPv6 literals
        return p!=-1 && host.substring(p+1).isNumber() ? host.substring(0,p) : host
    }

}
