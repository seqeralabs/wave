package io.seqera.auth

import groovy.json.JsonSlurper
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
@Builder
class ConfigurableAuthProvider implements DockerAuthProvider{

    private Map<String,String> tokenCache
    private HttpClient httpClient

    ConfigurableAuthProvider() {
        init()
    }

    private void init() {
        this.tokenCache = [:]
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()
    }

    String username

    String password

    String authUrl

    String service

    @Override
    void cleanTokenFor(String image) {
        if( tokenCache.containsKey(image) )
            tokenCache.remove(image)
    }

    @Override
    String getTokenFor(String image) {
        assert username
        assert password
        assert image

        if( tokenCache.containsKey(image) )
            return tokenCache[image]

        final basic = "$username:$password".bytes.encodeBase64()
        final auth = "Basic $basic"
        final login = "$authUrl?service=$service&scope=repository:${image}:pull"

        final req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(login))
                .setHeader("Authorization", auth.toString()) // add resp header
                .build()
        log.debug "Token request=$req"

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        final body = resp.body()
        final json = (Map) new JsonSlurper().parseText(body)
        if( resp.statusCode()==200 ) {
            tokenCache[image] = "$json.token".toString()
            return json.token
        }else
            throw new IllegalStateException("Unable to authorize request -- response: $body")
    }

}
