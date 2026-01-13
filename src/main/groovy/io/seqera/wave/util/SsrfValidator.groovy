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

import java.util.regex.Pattern

/**
 * Utility class to prevent Server-Side Request Forgery (SSRF) attacks
 * by validating URLs and hostnames before making HTTP requests.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
class SsrfValidator {

    // Private IP ranges (RFC 1918)
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
        '^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)'
    )

    // Loopback addresses
    private static final Pattern LOOPBACK_PATTERN = Pattern.compile(
        '^(127\\.|0\\.0\\.0\\.0$)'
    )

    // Link-local addresses
    private static final Pattern LINK_LOCAL_PATTERN = Pattern.compile(
        '^169\\.254\\.'
    )

    // Cloud metadata service IPs
    private static final Set<String> METADATA_IPS = [
        '169.254.169.254',  // AWS, GCP, Azure metadata service
        '169.254.170.2',    // AWS ECS metadata service
        'fd00:ec2::254'     // AWS IMDSv2 IPv6
    ] as Set

    // Localhost variations
    private static final Set<String> LOCALHOST_NAMES = [
        'localhost',
        'localhost.localdomain',
        '0.0.0.0',
        '0000:0000:0000:0000:0000:0000:0000:0001',
        '::1'
    ] as Set

    /**
     * Validates a URL to ensure it doesn't target internal/private resources
     *
     * @param url The URL to validate
     * @throws IllegalArgumentException if the URL is potentially malicious
     */
    static void validateUrl(String url) {
        if (!url) {
            throw new IllegalArgumentException("URL cannot be null or empty")
        }

        URI uri
        try {
            uri = new URI(url)
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: ${url}", e)
        }

        validateUri(uri)
    }

    /**
     * Validates a URI to ensure it doesn't target internal/private resources
     *
     * @param uri The URI to validate
     * @throws IllegalArgumentException if the URI is potentially malicious
     */
    static void validateUri(URI uri) {
        if (!uri) {
            throw new IllegalArgumentException("URI cannot be null")
        }

        // Validate scheme - only allow http and https
        def scheme = uri.scheme?.toLowerCase()
        if (scheme != 'http' && scheme != 'https') {
            throw new IllegalArgumentException("URL scheme must be http or https, got: ${scheme}")
        }

        def host = uri.host
        if (!host) {
            throw new IllegalArgumentException("URL must have a valid host")
        }

        validateHost(host)
    }

    /**
     * Validates a hostname to ensure it doesn't resolve to internal/private resources
     *
     * @param host The hostname to validate
     * @throws IllegalArgumentException if the hostname is potentially malicious
     */
    static void validateHost(String host) {
        if (!host) {
            throw new IllegalArgumentException("Host cannot be null or empty")
        }

        // Normalize host (lowercase, trim)
        host = host.toLowerCase().trim()

        // Check localhost variations
        if (LOCALHOST_NAMES.contains(host)) {
            throw new IllegalArgumentException("Access to localhost is not allowed: ${host}")
        }

        // Check if the host is a direct IP address (before DNS resolution)
        if (isIpAddress(host)) {
            // Direct IP address validation
            validateIpString(host)
        }

        // Try to resolve the host to IP address(es)
        try {
            def addresses = InetAddress.getAllByName(host)
            for (InetAddress addr : addresses) {
                validateIpAddress(addr)
            }
        } catch (UnknownHostException e) {
            // Host doesn't resolve - this is fine, let it fail naturally
            log.warn "Unable to resolve host: ${host} - ${e.message}"
        }
    }

    /**
     * Check if a string is an IP address
     */
    private static boolean isIpAddress(String host) {
        // Check if it looks like an IPv4 address
        if (host.matches('^\\d{1,3}(\\.\\d{1,3}){3}$')) {
            return true
        }
        // Check if it contains colons (likely IPv6)
        if (host.contains(':')) {
            return true
        }
        return false
    }

    /**
     * Validates an IP address string
     */
    private static void validateIpString(String ip) {
        // Check if it's in our block lists
        if (METADATA_IPS.contains(ip)) {
            throw new IllegalArgumentException("Access to cloud metadata service is not allowed: ${ip}")
        }

        // Check link-local
        if (LINK_LOCAL_PATTERN.matcher(ip).find()) {
            throw new IllegalArgumentException("Access to link-local address is not allowed: ${ip}")
        }

        // Check private IPs
        if (PRIVATE_IP_PATTERN.matcher(ip).find()) {
            throw new IllegalArgumentException("Access to private IP range is not allowed: ${ip}")
        }

        // Check loopback
        if (LOOPBACK_PATTERN.matcher(ip).find()) {
            throw new IllegalArgumentException("Access to loopback address is not allowed: ${ip}")
        }
    }

    /**
     * Validates an IP address to ensure it's not a private or internal address
     *
     * @param address The IP address to validate
     * @throws IllegalArgumentException if the IP address is private or internal
     */
    static void validateIpAddress(InetAddress address) {
        def ip = address.hostAddress

        // Check metadata service IPs
        if (METADATA_IPS.contains(ip)) {
            throw new IllegalArgumentException("Access to cloud metadata service is not allowed: ${ip}")
        }

        // Check if it's a site-local (private) address
        if (address.isSiteLocalAddress()) {
            throw new IllegalArgumentException("Access to private IP address is not allowed: ${ip}")
        }

        // Check if it's a loopback address
        if (address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Access to loopback address is not allowed: ${ip}")
        }

        // Check if it's a link-local address
        if (address.isLinkLocalAddress()) {
            throw new IllegalArgumentException("Access to link-local address is not allowed: ${ip}")
        }

        // Additional regex-based checks for IPv4
        if (address instanceof Inet4Address) {
            // Check private IP ranges
            if (PRIVATE_IP_PATTERN.matcher(ip).find()) {
                throw new IllegalArgumentException("Access to private IP range is not allowed: ${ip}")
            }

            // Check loopback
            if (LOOPBACK_PATTERN.matcher(ip).find()) {
                throw new IllegalArgumentException("Access to loopback address is not allowed: ${ip}")
            }

            // Check link-local
            if (LINK_LOCAL_PATTERN.matcher(ip).find()) {
                throw new IllegalArgumentException("Access to link-local address is not allowed: ${ip}")
            }
        }

        // Check for IPv6 unique local addresses (fc00::/7)
        if (address instanceof Inet6Address) {
            byte[] bytes = address.address
            // Check if first byte is 0xfc or 0xfd (unique local addresses)
            if ((bytes[0] & 0xfe) == 0xfc) {
                throw new IllegalArgumentException("Access to IPv6 unique local address is not allowed: ${ip}")
            }
        }
    }

    /**
     * Checks if a URL is safe without throwing an exception
     *
     * @param url The URL to check
     * @return true if the URL is safe, false otherwise
     */
    static boolean isSafeUrl(String url) {
        try {
            validateUrl(url)
            return true
        } catch (Exception e) {
            log.debug "URL validation failed: ${e.message}"
            return false
        }
    }

    /**
     * Checks if a host is safe without throwing an exception
     *
     * @param host The host to check
     * @return true if the host is safe, false otherwise
     */
    static boolean isSafeHost(String host) {
        try {
            validateHost(host)
            return true
        } catch (Exception e) {
            log.debug "Host validation failed: ${e.message}"
            return false
        }
    }
}
