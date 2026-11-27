/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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

package io.seqera.wave.proxy

import java.net.http.HttpResponse

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.http.HttpClientFactory
import spock.lang.Shared
import spock.lang.Specification
/**
 * Verify the proxy client re-validates redirect targets when SSRF protection
 * is enabled: a registry that passed validation at lookup time must not be able
 * to redirect the client into a private network.
 *
 * @author Oussama El Azizi <oussama.elazizi@seqera.io>
 */
class ProxyClientSsrfRedirectTest extends Specification {

    @Shared
    HttpServer server

    @Shared
    int port

    def setupSpec() {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        port = server.address.port
        server.createContext('/v2/redirect-private', { HttpExchange exchange ->
            exchange.responseHeaders.add('Location', 'https://10.0.0.1/v2/evil')
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        } as HttpHandler)
        server.createContext('/v2/redirect-metadata', { HttpExchange exchange ->
            exchange.responseHeaders.add('Location', 'https://169.254.169.254/latest/meta-data/')
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        } as HttpHandler)
        server.createContext('/v2/redirect-relative', { HttpExchange exchange ->
            exchange.responseHeaders.add('Location', '/v2/final')
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        } as HttpHandler)
        server.createContext('/v2/final', { HttpExchange exchange ->
            final body = 'OK'.bytes
            exchange.sendResponseHeaders(200, body.length)
            exchange.responseBody.write(body)
            exchange.close()
        } as HttpHandler)
        server.start()
    }

    def cleanupSpec() {
        server?.stop(0)
    }

    private ProxyClient client(boolean ssrfProtection) {
        final endpoint = new URI("http://localhost:$port")
        final registry = new RegistryInfo('localhost', endpoint, new RegistryAuth(endpoint))
        return new ProxyClient(HttpClientFactory.neverRedirectsHttpClient(), new HttpClientConfig())
                .withImage('probe')
                .withRegistry(registry)
                .withLoginService(Mock(RegistryAuthService))
                .withSsrfProtection(ssrfProtection)
    }

    private HttpResponse<String> get(ProxyClient proxy, String path) {
        return proxy.get0(
                new URI("http://localhost:$port$path"),
                null,
                HttpResponse.BodyHandlers.ofString(),
                true )
    }

    def 'should refuse a redirect to a private address'() {
        when:
        get(client(true), '/v2/redirect-private')

        then:
        def e = thrown(ClientResponseException)
        e.message.contains('Redirect location not allowed')
        e.message.contains('10.0.0.1')
    }

    def 'should refuse a redirect to the cloud metadata address'() {
        when:
        get(client(true), '/v2/redirect-metadata')

        then:
        def e = thrown(ClientResponseException)
        e.message.contains('Redirect location not allowed')
        e.message.contains('169.254.169.254')
    }

    def 'should refuse a relative redirect resolving to a loopback address'() {
        when:
        get(client(true), '/v2/redirect-relative')

        then:
        def e = thrown(ClientResponseException)
        e.message.contains('Redirect location not allowed')
    }

    def 'should follow redirects when protection is disabled'() {
        when:
        def resp = get(client(false), '/v2/redirect-relative')

        then:
        resp.statusCode() == 200
        resp.body() == 'OK'
    }

    def 'should not interfere with direct responses when protection is enabled'() {
        when:
        def resp = get(client(true), '/v2/final')

        then:
        resp.statusCode() == 200
        resp.body() == 'OK'
    }

}
