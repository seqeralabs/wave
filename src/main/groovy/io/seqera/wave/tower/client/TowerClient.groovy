package io.seqera.wave.tower.client

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton
import org.apache.commons.lang.StringUtils
/**
 * Tower API client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(property = 'tower.api.endpoint')
@Slf4j
@Singleton
@CompileStatic
class TowerClient {

    private HttpClient httpClient

    private URI userInfoEndpoint

    TowerClient(@Value('${tower.api.endpoint}')String endpoint) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build()
        if( !endpoint )
            throw new IllegalArgumentException("Missing Tower endpoint")
        // cleanup ending slashes
        endpoint = StringUtils.stripEnd(endpoint, '/')
        log.debug "Tower client endpoint=$endpoint"
        this.userInfoEndpoint = new URI("${endpoint}/user-info")
    }

    CompletableFuture<UserInfoResponse> userInfo(String authorization) {
        final req = HttpRequest.newBuilder()
                .uri(userInfoEndpoint)
                .headers('Content-Type', 'application/json', 'Authorization', "Bearer $authorization")
                .GET()
                .build()

            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply((resp)-> {
                log.debug "Tower auth response: [${resp.statusCode()}] ${resp.body()}"
                switch (resp.statusCode()) {
                    case 200:
                        return JacksonHelper.fromJson(resp.body(), UserInfoResponse)
                        break
                    case 401:
                        throw new HttpResponseException(401, "Unauthorized")
                        break
                    default:
                        throw new HttpResponseException(resp.statusCode(), resp.body())
                }
            })
        }
}
