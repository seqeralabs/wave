/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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
import io.seqera.wave.exception.BadRequestException

/**
 * Utility class to prevent Server-Side Request Forgery (SSRF) attacks
 * by validating hostnames before making HTTP requests.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
class SsrfValidator {

    // Cloud metadata service IPs
    private static final Set<String> METADATA_IPS = [
        '169.254.169.254',  // AWS metadata service- https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instancedata-data-retrieval.html
        '169.254.170.2',    // AWS ECS metadata service - https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html
        'fd00:ec2::254'     // AWS IMDSv2 IPv6 - https://aws.amazon.com/blogs/aws/amazon-ec2-instance-metadata-service-imdsv2-by-default/
    ] as Set

    // Localhost variations that should be rejected before DNS resolution
    private static final Set<String> LOCALHOST_NAMES = [
        'localhost',
        'localhost.localdomain',
        '0.0.0.0',
        '0000:0000:0000:0000:0000:0000:0000:0001',
        '::1' // https://datatracker.ietf.org/doc/html/rfc4291#section-2.5.3
    ] as Set

    /**
     * Validates a hostname to ensure it doesn't resolve to internal/private resources
     *
     * @param host The hostname to validate
     * @throws BadRequestException if the hostname is potentially malicious
     */
    static void validateHost(String host) {
        if (!host) {
            throw new BadRequestException("Host cannot be null or empty")
        }

        // Normalize host (lowercase, trim)
        host = host.toLowerCase().trim()

        // Extract hostname from URL if scheme is present
        host = extractHostname(host)

        // Check localhost variations
        if (LOCALHOST_NAMES.contains(host)) {
            throw new BadRequestException("Access to localhost is not allowed: ${host}")
        }

        // Resolve the host to IP address(es) and validate each
        try {
            def addresses = InetAddress.getAllByName(host)
            for (InetAddress addr : addresses) {
                validateIpAddress(addr)
            }
        } catch (UnknownHostException e) {
            // Fail closed - reject hosts that cannot be resolved
            throw new BadRequestException("Unable to resolve host: ${host}")
        }
    }

    /**
     * Extracts the hostname from a registry string
     */
    private static String extractHostname(String host) {
        if (host.startsWith('http://') || host.startsWith('https://')) {
            try {
                return new URI(host).getHost()
            } catch (URISyntaxException ignored) {
                return host
            }
        }
        // Handle bracketed IPv6 with optional port: [::1]:8080
        if (host.startsWith('[')) {
            int closeBracket = host.indexOf(']')
            if (closeBracket > 0) {
                return host.substring(1, closeBracket)
            }
        }
        // Strip port from bare host:port (e.g. 192.168.1.1:5000)
        // Only when there is exactly one colon (not IPv6 which has multiple)
        int colonIdx = host.lastIndexOf(':')
        if (colonIdx > 0 && host.indexOf(':') == colonIdx) {
            return host.substring(0, colonIdx)
        }
        return host
    }

    /**
     * Validates an InetAddress to ensure it's not a private or internal address
     */
    private static void validateIpAddress(InetAddress address) {
        def ip = address.hostAddress

        // Check metadata service IPs first (before link-local, for a specific error message)
        if (METADATA_IPS.contains(ip)) {
            log.debug("SSRF validation rejected cloud metadata service IP: ${ip}")
            throw new BadRequestException("Invalid registry hostname")
        }

        if (address.isLoopbackAddress()) {
            log.debug("SSRF validation rejected loopback address: ${ip}")
            throw new BadRequestException("Invalid registry hostname")
        }

        if (address.isLinkLocalAddress()) {
            log.debug("SSRF validation rejected link-local address: ${ip}")
            throw new BadRequestException("Invalid registry hostname")
        }

        if (address.isSiteLocalAddress()) {
            log.debug("SSRF validation rejected private IP address: ${ip}")
            throw new BadRequestException("Invalid registry hostname")
        }

        // Check for IPv6 unique local addresses (fc00::/7)
        if (address instanceof Inet6Address) {
            byte[] bytes = address.address
            if ((bytes[0] & 0xfe) == 0xfc) {
                log.debug("SSRF validation rejected IPv6 unique local address: ${ip}")
                throw new BadRequestException("Invalid registry hostname")
            }
        }
    }
}
