package io.seqera.wave.stats.surrealdb

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.seqera.wave.stats.BuildBean
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux


/**
 * An http client to access to a SurrealDB
 *
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='surreal')
@CompileStatic
@Header(name = "Content-type", value = "application/json")
@Header(name = "ns", value = '${stats.surrealdb.ns}')
@Header(name = "db", value = '${stats.surrealdb.db}')
@Client(value = '${stats.surrealdb.url}')
interface SurrealClient {

    @Post("/sql")
    Flux<Map<String, String>> sqlAsync(@Header String authorization, @Body String body)

    @Post("/sql")
    Map<String, String> sql(@Header String authorization, @Body String body)

    @Post("/key/build_wave")
    Flux<Map<String, String>> insertBuildAsync(@Header String authorization, BuildBean body)

    @Post("/key/build_wave")
    Map<String, String> insertBuild(@Header String authorization, BuildBean body)

}
