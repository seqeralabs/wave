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

package io.seqera.wave.service.persistence.impl

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import reactor.core.publisher.Flux
/**
 * Declarative http client for SurrealDB
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='surrealdb')
@CompileStatic
@Header(name = "Content-type", value = "application/json")
@Header(name = "ns", value = '${surrealdb.ns}')
@Header(name = "db", value = '${surrealdb.db}')
@Client(value = '${surrealdb.url}')
@Retryable(
        delay = '${wave.surreal.retry.delay:1s}',
        maxDelay = '${wave.surreal.retry.maxDelay:10s}',
        attempts = '${wave.surreal.retry.attempts:3}',
        multiplier = '${wave.surreal.retry.multiplier:1.5}',
        predicate = RetryOnIOException )
interface SurrealClient {

    @Post("/sql")
    Flux<Map<String, Object>> sqlAsync(@Header String authorization, @Body String body)

    @Post("/sql")
    Map<String, Object> sqlAsMap(@Header String authorization, @Body String body)

    @Post("/sql")
    String sqlAsString(@Header String authorization, @Body String body)

    @Post('/key/wave_build')
    Flux<Map<String, Object>> insertBuildAsync(@Header String authorization, @Body WaveBuildRecord body)

    @Post('/key/wave_build')
    Map<String, Object> insertBuild(@Header String authorization, @Body WaveBuildRecord body)

    @Get('/key/wave_request/{token}')
    String getContainerRequest(@Header String authorization, String token)

    @Post('/key/wave_request/{token}')
    Flux<Map<String, Object>> insertContainerRequestAsync(@Header String authorization, String token, @Body WaveContainerRecord body)

    @Put('/key/wave_request/{token}')
    Flux<Map<String, Object>> updateContainerRequestAsync(@Header String authorization, String token, @Body WaveContainerRecord body)

    @Post('/key/wave_scan')
    Map<String,Object> insertScanRecord(@Header String authorization, @Body WaveScanRecord body)

    @Post('/key/wave_scan_vuln')
    Map<String, Object> insertScanVulnerability(@Header String authorization, @Body ScanVulnerability scanVulnerability)

}
