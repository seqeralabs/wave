package io.seqera.wave.service.persistence.impl

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.seqera.wave.service.persistence.BuildRecord
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
interface SurrealClient {

    @Post("/sql")
    Flux<Map<String, Object>> sqlAsync(@Header String authorization, @Body String body)

    @Post("/sql")
    Map<String, Object> sqlAsMap(@Header String authorization, @Body String body)

    @Post("/sql")
    String sqlAsString(@Header String authorization, @Body String body)

    @Post('/key/wave_build')
    Flux<Map<String, Object>> insertBuildAsync(@Header String authorization, @Body BuildRecord body)

    @Post('/key/wave_build')
    Map<String, Object> insertBuild(@Header String authorization, @Body BuildRecord body)

}
