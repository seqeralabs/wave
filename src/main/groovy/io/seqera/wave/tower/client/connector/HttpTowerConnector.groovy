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

package io.seqera.wave.tower.client.connector


import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpMethod
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements a Tower client using a plain HTTP client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class HttpTowerConnector extends TowerConnector {

    @Inject
    private HttpClientConfig config

    @Override
    CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request) {
        final client = HttpClientFactory.neverRedirectsHttpClient()
        client
            .sendAsync(buildHttpRequest(request), HttpResponse.BodyHandlers.ofString())
            .thenApply( (resp)-> {
                return new ProxyHttpResponse(
                        msgId: request.msgId,
                        status: resp.statusCode(),
                        body: resp.body(),
                        headers: resp.headers().map())
                })
    }

    private static HttpRequest buildHttpRequest(ProxyHttpRequest proxyRequest) {

        final builder = HttpRequest
                .newBuilder()
                .uri(URI.create(proxyRequest.uri))

        final method = HttpMethod.parse(proxyRequest.method)
        switch (method) {
            case HttpMethod.GET: builder.GET(); break
            case HttpMethod.DELETE: builder.DELETE(); break
            default:
                builder.method(method.toString(), HttpRequest.BodyPublishers.ofString(proxyRequest.body))
        }

        if (proxyRequest.auth)
            builder.header('Authorization', proxyRequest.auth)

        final headers = proxyRequest.headers ?: Map.<String,List<String>>of()
        for( Map.Entry<String,List<String>> header : headers ) {
            for( String it : (header.value ?: List.of()) ) {
                builder.header(header.key, it)
            }
        }

        return builder.build()
    }

}
