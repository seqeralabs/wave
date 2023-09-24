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

package io.seqera.wave.http

import java.net.http.HttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    static private ExecutorService virtualThreadsExecutor = Executors.newVirtualThreadPerTaskExecutor()

    static private HttpClient INSTANCE

    static private final Integer hold = Integer.valueOf(0)

    @Inject
    HttpClientConfig httpConfig

    @Singleton
    @Named("follow-redirects")
    HttpClient followRedirectsHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(httpConfig.connectTimeout)
                .executor(virtualThreadsExecutor)
                .build()
    }

    @Singleton
    @Named("never-redirects")
    HttpClient neverRedirectsHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(httpConfig.connectTimeout)
                .executor(virtualThreadsExecutor)
                .build()
    }

    static HttpClient newHttpClient() {
        if( INSTANCE ) return INSTANCE
        synchronized (hold) {
            if( INSTANCE ) return INSTANCE
            return INSTANCE = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .executor(virtualThreadsExecutor)
                    .build()
        }
    }
}
