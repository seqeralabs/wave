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
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
/**
 * Java HttpClient factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class HttpClientFactory {

    static private ExecutorService virtualThreadsExecutor = Executors.newVirtualThreadPerTaskExecutor()

    static private Duration timeout = Duration.ofSeconds(20)

    static private Cache<String, HttpClient> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    static HttpClient followRedirectsHttpClient() {
        cache.get('followRedirectsHttpClient', ()-> followRedirectsHttpClient0())
    }

    static HttpClient neverRedirectsHttpClient() {
        cache.get('neverRedirectsHttpClient', ()-> neverRedirectsHttpClient0())
    }

    static HttpClient newHttpClient() {
        return followRedirectsHttpClient()
    }

    static private HttpClient followRedirectsHttpClient0() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .executor(virtualThreadsExecutor)
                .build()
    }

    static private HttpClient neverRedirectsHttpClient0() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .executor(virtualThreadsExecutor)
                .build()
    }
}
