package io.seqera.wave.stats.surrealdb

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.seqera.wave.stats.BuildRecord
import reactor.core.publisher.Flux


/**
 * An http client to access to a SurrealDB
 *
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
interface SurrealClient {

    @Post("/sql")
    Flux<Map<String, Object>> sqlAsync(@Header String authorization, @Body String body)

    @Post("/sql")
    Map<String, Object> sql(@Header String authorization, @Body String body)

    @Post(value = SurrealConstants.BUILD_WAVE_ENDPOINT)
    Flux<Map<String, Object>> insertBuildAsync(@Header String authorization, @Body BuildRecord body)

    @Post(SurrealConstants.BUILD_WAVE_ENDPOINT)
    Map<String, Object> insertBuild(@Header String authorization, @Body BuildRecord body)

}
