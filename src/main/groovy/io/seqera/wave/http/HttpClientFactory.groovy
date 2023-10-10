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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.util.CustomThreadFactory
/**
 * Java HttpClient factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class HttpClientFactory {

    static private ExecutorService threadPool = Executors.newCachedThreadPool(new CustomThreadFactory("HttpClientThread"))

    static private Duration timeout = Duration.ofSeconds(20)

    static private final Object l1 = new Object()

    static private final Object l2 = new Object()

    private static HttpClient client1

    private static HttpClient client2


    static HttpClient followRedirectsHttpClient() {
        if( client1!=null )
            return client1
        synchronized (l1) {
            if( client1!=null )
                return client1
            return client1=followRedirectsHttpClient0()
        }
    }

    static HttpClient neverRedirectsHttpClient() {
        if( client2!=null )
            return client2
        synchronized (l2) {
            if( client2!=null )
                return client2
            return client2=neverRedirectsHttpClient0()
        }
    }

    static HttpClient newHttpClient() {
        return followRedirectsHttpClient()
    }

    static private HttpClient followRedirectsHttpClient0() {
        final result = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .executor(threadPool)
                .build()
        log.debug "Creating new followRedirectsHttpClient: $result"
        return result
    }

    static private HttpClient neverRedirectsHttpClient0() {
        final result = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .executor(threadPool)
                .build()
        log.debug "Creating new neverRedirectsHttpClient: $result"
        return result
    }

}
