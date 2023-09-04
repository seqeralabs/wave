package io.seqera.wave.http

import java.net.http.HttpClient
import java.util.concurrent.Executors

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
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

    @Value("wave.thread.virtual.enable")
    boolean useVirtualThread

    @Singleton
    @Named("follow-redirects")
    HttpClient followRedirectsHttpClient() {
        final builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(httpConfig.connectTimeout)
        // use virtual threads executor if enabled
        if(useVirtualThread){
            builder.executor(Executors.newVirtualThreadPerTaskExecutor())
        }
        builder.build()
    }

    @Singleton
    @Named("never-redirects")
    HttpClient neverRedirectsHttpClient() {
        final builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(httpConfig.connectTimeout)
        // use virtual threads executor if enabled
        if(useVirtualThread){
            builder.executor(Executors.newVirtualThreadPerTaskExecutor())
        }
        builder.build()
    }
}
