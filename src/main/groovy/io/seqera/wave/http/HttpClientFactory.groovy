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

    @Value("wave.virtualThreads.enabled")
    boolean useVirtualThread

    @Singleton
    @Named("follow-redirects")
    HttpClient followRedirectsHttpClient() {
        final client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(httpConfig.connectTimeout)
        // use virtual threads executor if enabled
        if(useVirtualThread){
            client.executor(Executors.newVirtualThreadPerTaskExecutor())
        }
        return client.build()
    }

    @Singleton
    @Named("never-redirects")
    HttpClient neverRedirectsHttpClient() {
        final client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(httpConfig.connectTimeout)
        // use virtual threads executor if enabled
        if(useVirtualThread){
            client.executor(Executors.newVirtualThreadPerTaskExecutor())
        }
        return client.build()
    }
}
