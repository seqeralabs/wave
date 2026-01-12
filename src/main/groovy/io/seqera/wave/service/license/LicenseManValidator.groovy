/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

package io.seqera.wave.service.license

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.service.pairing.LicenseValidator
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Bridge implementation that adapts the Wave LicenseManClient to the
 * lib-pairing LicenseValidator interface.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
@Requires(bean = LicenseManClient)
class LicenseManValidator implements LicenseValidator {

    @Inject
    private LicenseManClient licenseManClient

    @Override
    LicenseCheckResult checkToken(String token, String product) {
        final response = licenseManClient.checkToken(token, product)
        if (response == null) {
            return null
        }
        return new LicenseCheckResult(
            id: response.id,
            expiration: response.expiration
        )
    }
}
