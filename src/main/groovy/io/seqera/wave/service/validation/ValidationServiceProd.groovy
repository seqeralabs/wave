/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
