package io.seqera.wave.tower.client

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpStatus
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton
import org.apache.commons.lang.StringUtils
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoSink

/**
 * Tower API client
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env = 'tower')
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

    Mono<UserInfoResponse> userInfo(String authorization) {
        Mono.<UserInfoResponse>create { MonoSink<UserInfoResponse> emitter ->
            final req = HttpRequest.newBuilder()
                    .uri(userInfoEndpoint)
                    .headers('Content-Type', 'application/json', 'Authorization', "Bearer $authorization")
                    .GET()
                    .build()

            try {
                httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept((resp)-> {
                    log.debug "Tower auth response: [${resp.statusCode()}] ${resp.body()}"
                    switch (resp.statusCode()) {
                        case 200:
                            emitter.success(JacksonHelper.fromJson(resp.body(), UserInfoResponse))
                            break
                        case 401:
                            emitter.error(new HttpResponseException(401, "Unauthorized"))
                            break
                        default:
                            emitter.error(new HttpResponseException(resp.statusCode(), resp.body()))
                    }
                })
            }
            catch (HttpResponseException e) {
                emitter.error(e)
            }
            catch (Throwable e) {
                emitter.error(new HttpResponseException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error = ${e.message}", e))
            }
        }
    }
}
