/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.service.persistence.legacy

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.service.persistence.impl.RetryOnIOException
/**
 * Declarative http client for SurrealDB. This is only meant to be used for backward
 * compatibility with Surreal pre-1.0 version
 *
 * @author : Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 *
 */
@Requires(property = 'surreal.legacy.url')
@CompileStatic
@Header(name = "Content-type", value = "application/json")
@Header(name = "ns", value = '${surreal.legacy.ns}')
@Header(name = "db", value = '${surreal.legacy.db}')
@Client(value = '${surreal.legacy.url}')
@Retryable(
        delay = '${wave.surreal-legacy.retry.delay:1s}',
        maxDelay = '${wave.surreal-legacy.retry.maxDelay:10s}',
        attempts = '${wave.surreal-legacy.retry.attempts:3}',
        multiplier = '${wave.surreal-legacy.retry.multiplier:1.5}',
        predicate = RetryOnIOException )
interface SurrealLegacyClient {

    @Post("/sql")
    String sqlAsString(@Header String authorization, @Body String body)

    @Get('/key/wave_request/{token}')
    String getContainerRequest(@Header String authorization, String token)

}
