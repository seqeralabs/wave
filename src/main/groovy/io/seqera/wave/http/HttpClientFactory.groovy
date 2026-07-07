/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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
import java.util.concurrent.locks.ReentrantLock

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

    static private final ReentrantLock l1 = new ReentrantLock()

    static private final ReentrantLock l2 = new ReentrantLock()

    private static HttpClient client1

    private static HttpClient client2

    private static volatile HttpProxyConfig proxyConfig


    /**
     * Set the egress proxy configuration to be used by the clients created by this factory.
     * The proxy is resolved at bootstrap by {@link io.seqera.wave.configuration.HttpClientConfig}.
     * Cached client instances are discarded so that the new settings are applied to clients
     * obtained after this call
     *
     * @param config The {@link HttpProxyConfig} to be applied, or {@code null} to use no proxy
     */
    static void setProxyConfig(HttpProxyConfig config) {
        if( config == null && proxyConfig == null )
            return
        l1.lock()
        try {
            proxyConfig = config
            client1 = null
        }
        finally {
            l1.unlock()
        }
        l2.lock()
        try {
            client2 = null
        }
        finally {
            l2.unlock()
        }
    }

    static private HttpClient.Builder applyProxyConfig(HttpClient.Builder builder) {
        final proxy = proxyConfig
        if( proxy ) {
            builder.proxy(proxy.proxySelector())
            final auth = proxy.authenticator()
            if( auth )
                builder.authenticator(auth)
        }
        return builder
    }

    static HttpClient followRedirectsHttpClient() {
        if( client1!=null )
            return client1
        l1.lock()
        try {
            if( client1!=null )
                return client1
            return client1=newHttpClient0(HttpClient.Redirect.NORMAL)
        } finally {
            l1.unlock()
        }
    }

    static HttpClient neverRedirectsHttpClient() {
        if( client2!=null )
            return client2
        l2.lock()
        try {
            if( client2!=null )
                return client2
            return client2=newHttpClient0(HttpClient.Redirect.NEVER)
        } finally {
            l2.unlock()
        }
    }

    static HttpClient newHttpClient() {
        return followRedirectsHttpClient()
    }

    static private HttpClient newHttpClient0(HttpClient.Redirect redirect) {
        final builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(redirect)
                .connectTimeout(timeout)
                .executor(threadPool)
        final result = applyProxyConfig(builder).build()
        log.debug "Creating new httpClient with $redirect redirects policy: $result"
        return result
    }

}
