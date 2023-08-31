package io.seqera.wave.http

import java.net.http.HttpClient

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.seqera.wave.configuration.HttpClientConfig
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 * Java HttpClient factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@Slf4j
@CompileStatic
class HttpClientFactory {

    @Inject
    HttpClientConfig httpConfig

    @Singleton
    @Named("follow-redirects")
    HttpClient followRedirectsHttpClient() {
        HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(httpConfig.connectTimeout)
                .build()
    }

    @Singleton
    @Named("never-redirects")
    HttpClient neverRedirectsHttpClient() {
        HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(httpConfig.connectTimeout)
                .build()
    }
}
