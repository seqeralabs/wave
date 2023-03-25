package io.seqera.wave.tower.client.connector

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpMethod
import io.seqera.wave.configuration.HttpClientConfig
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

    private HttpClient client

    @PostConstruct
    void init() {
        this.client = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(config.connectTimeout)
                                .build()
    }

    @Override
    CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request) {
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

        if (proxyRequest.bearerAuth)
            builder.header('Authorization', "Bearer ${proxyRequest.bearerAuth}")

        final headers = proxyRequest.headers ?: Map.<String,List<String>>of()
        for( Map.Entry<String,List<String>> header : headers ) {
            for( String it : (header.value ?: List.of()) ) {
                builder.header(header.key, it)
            }
        }

        return builder.build()
    }

}
