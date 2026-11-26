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

import com.google.common.net.InetAddresses
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.util.net.SsrfValidationException
import io.seqera.util.net.SsrfValidator
import io.seqera.wave.configuration.SsrfConfig
import io.seqera.wave.exception.BadRequestException
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
     * A host that matches an explicit IP or CIDR entry in the allowlist is exempt from the
     * blocked-range rules, so an operator can permit a specific internal registry address.
     * Hostname/wildcard allowlist entries do not grant this exemption.
     *
     * @param host The registry host name, without scheme or port
     * @throws BadRequestException when the host is not allowed
     */
    void checkHost(String host) {
        if( !enabled )
            return
        // an explicit IP/CIDR allowlist entry overrides the blocked-range rules
        final allowlist = config.registryAllowlist
        if( allowlist && matchesIpOrCidr(host, allowlist) ) {
            log.debug "SSRF protection: host '${host}' is explicitly allow-listed by IP/CIDR - skipping range checks"
            return
        }
        try {
            SsrfValidator.validateHost(host)
        }
        catch (SsrfValidationException e) {
            log.warn "SSRF protection rejected registry host '${host}' - reason: ${e.message}"
            throw new BadRequestException(e.message)
        }
    }

    /**
     * Enforce the registry allowlist. When a list is configured, every request —
     * authenticated or not — may only reference an allow-listed registry. When no list is
     * configured no allowlist restriction is applied (callers are still subject to
     * {@link #checkHost} at lookup time).
     *
     * @param registry The registry name referenced by the request e.g. {@code quay.io}
     * @throws BadRequestException when the registry is not in the configured allowlist
     */
    void checkRegistryAllowed(String registry) {
        if( !enabled )
            return
        final allowlist = config.registryAllowlist
        if( !allowlist )
            return
        if( !isAllowed(registry, allowlist) ) {
            log.warn "SSRF protection rejected access to registry '${registry}' - allowed registries: ${allowlist}"
            throw new BadRequestException("Container registry '${registry}' is not in the configured registry allowlist")
        }
    }

    /**
     * Match the registry host against the allowlist. Entries may be:
     * <ul>
     *   <li>a hostname e.g. {@code quay.io}</li>
     *   <li>a wildcard host e.g. {@code *.amazonaws.com} (matches any subdomain and the bare domain)</li>
     *   <li>an IP literal e.g. {@code 203.0.113.5} (IPv4 or IPv6)</li>
     *   <li>a CIDR range e.g. {@code 203.0.113.0/24} or {@code 2001:db8::/32}</li>
     * </ul>
     * IP and CIDR entries are matched only when the registry itself is named by an IP literal;
     * no DNS resolution is performed, so a hostname that resolves into an allow-listed range does
     * not match.
     */
    static protected boolean isAllowed(String registry, List<String> allowlist) {
        if( !registry )
            return false
        final host = stripPort(registry.toLowerCase())
        // parse the registry host as an IP literal WITHOUT DNS; null when it is a hostname
        final hostIp = InetAddresses.isInetAddress(host) ? InetAddresses.forString(host) : null
        for( String entry : allowlist ) {
            final raw = entry?.trim()?.toLowerCase()
            if( !raw )
                continue
            // CIDR entry e.g. 10.0.0.0/24 — matches only an IP-named registry inside the range
            if( raw.contains('/') ) {
                if( hostIp != null && cidrContains(raw, hostIp) )
                    return true
                continue
            }
            final pattern = stripPort(raw)
            // wildcard host e.g. *.example.com
            if( pattern.startsWith('*.') ) {
                if( host.endsWith(pattern.substring(1)) || host==pattern.substring(2) )
                    return true
            }
            // IP literal entry e.g. 203.0.113.5 — compare as addresses to normalise equivalent forms
            else if( hostIp != null && InetAddresses.isInetAddress(pattern) ) {
                if( hostIp == InetAddresses.forString(pattern) )
                    return true
            }
            // exact hostname match
            else if( host==pattern )
                return true
        }
        return false
    }

    /**
     * Whether {@code host} is an IP literal explicitly permitted by an IP or CIDR allowlist entry.
     * Hostname and wildcard entries are ignored on purpose: only an explicit IP/CIDR entry may
     * override the blocked-range rules, so a (potentially rebinding) allow-listed hostname cannot
     * be used to reach an internal address. No DNS resolution is performed.
     */
    static protected boolean matchesIpOrCidr(String host, List<String> allowlist) {
        final cleaned = stripPort(host?.toLowerCase())
        if( !cleaned || !InetAddresses.isInetAddress(cleaned) )
            return false
        final hostIp = InetAddresses.forString(cleaned)
        for( String entry : allowlist ) {
            final raw = entry?.trim()?.toLowerCase()
            if( !raw )
                continue
            if( raw.contains('/') ) {
                if( cidrContains(raw, hostIp) )
                    return true
            }
            else {
                final pattern = stripPort(raw)
                if( InetAddresses.isInetAddress(pattern) && hostIp==InetAddresses.forString(pattern) )
                    return true
            }
        }
        return false
    }

    /**
     * Test IPv4/IPv6 CIDR membership by comparing the first {@code prefix} bits; no DNS.
     */
    static private boolean cidrContains(String cidr, InetAddress address) {
        final slash = cidr.indexOf('/')
        final baseStr = slash > 0 ? cidr.substring(0, slash) : null
        if( !baseStr || !InetAddresses.isInetAddress(baseStr) )
            return false
        final int prefix
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1))
        }
        catch( NumberFormatException e ) {
            return false
        }
        final byte[] base = InetAddresses.forString(baseStr).address
        final byte[] addr = address.address
        // address family must match (both IPv4 or both IPv6)
        if( base.length != addr.length || prefix < 0 || prefix > base.length * 8 )
            return false
        int bits = prefix
        for( int i = 0; i < base.length && bits > 0; i++, bits -= 8 ) {
            final int mask = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF
            if( (base[i] & mask) != (addr[i] & mask) )
                return false
        }
        return true
    }

    static private String stripPort(String host) {
        if( !host )
            return host
        // bracketed IPv6 literal e.g. [2001:db8::1] or [2001:db8::1]:443
        if( host.startsWith('[') ) {
            final close = host.indexOf(']')
            return close > 0 ? host.substring(1, close) : host
        }
        final p = host.lastIndexOf(':')
        // strip a trailing :port only for host:port forms (single colon); a bare IPv6 literal has
        // multiple colons and must be left intact
        return p!=-1 && host.indexOf(':')==p && host.substring(p+1).isNumber() ? host.substring(0,p) : host
    }

}
