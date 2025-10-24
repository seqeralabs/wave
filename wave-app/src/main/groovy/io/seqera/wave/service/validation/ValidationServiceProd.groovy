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

package io.seqera.wave.service.validation

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Validation service for production deployment
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Primary
@Requires(env = 'prod')
@Singleton
@CompileStatic
class ValidationServiceProd implements ValidationService {

    @Inject
    @Delegate
    ValidationServiceImpl defaultValidation

    @Override
    String checkEndpoint(String endpoint) {
        def err = defaultValidation.checkEndpoint(endpoint)
        if( err )
            return err
        final uri = new URI(endpoint)
        if( !isValidHostname(uri.host) )
            return "Endpoint hostname not allowed — offending value: $endpoint"

        return null
    }


    private static final Pattern PRIVATE_IP_PATTERN = ~/(127\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|0?1[02]\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|172\.0?1[6-9]\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|172\.0?2[0-9]\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|172\.0?3[0-7]\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|192\.168\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|::1|[fF][cCdD][0-9a-fA-F]{2}(?:[:][0-9a-fA-F]{0,4}){0,7})(?:\/([789]|1?[0-9]{2}))?/

    /**
     * Checks whether a given hostname is valid (it's not a private IPv4 or IPv6 address, and it's not a top-level domain).
     * https://stackoverflow.com/a/62925185/5144316
     * https://tools.ietf.org/html/rfc1918
     *
     * @param The hostname to check
     */
    static boolean isValidHostname(String hostname) {
        final normalizedHostname = hostname.trim()

        final isTopLevelDomain = !normalizedHostname.contains('.') && !normalizedHostname.contains(':')
        if (isTopLevelDomain) return false

        final isPrivateIpAddress = PRIVATE_IP_PATTERN.matcher(normalizedHostname).matches()
        return !isPrivateIpAddress
    }
}
