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

package io.seqera.wave.service.license

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
/**
 * Declare client for license manager
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(property = 'license.server.url')
@Header(name = "User-Agent", value = "Wave service")
@Client(value = '${license.server.url}')
@Retryable(
        delay = '${license.retry.delay:1s}',
        maxDelay = '${license.retry.maxDelay:10s}',
        attempts = '${license.retry.attempts:3}',
        multiplier = '${license.retry.multiplier:1.5}',
        predicate = LicenceManRetryPredicate
)
interface LicenseManClient {

    @Get('/check')
    CheckTokenResponse checkToken(@QueryValue("token") String token, @QueryValue("product") String product)

}
