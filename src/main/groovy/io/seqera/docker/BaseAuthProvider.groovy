package io.seqera.docker

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
abstract class BaseAuthProvider implements DockerAuthProvider{

    private Map<String,String> tokenCache
    private HttpClient httpClient

    BaseAuthProvider() {
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

    protected abstract String getUsername()

    protected abstract String getPassword()

    protected abstract String getAuthUrl()

    protected abstract String getService()

    @Override
    void cleanTokenForImage(String image) {
        if( tokenCache.containsKey(image) )
            tokenCache.remove(image)
    }

    @Override
    String getTokenForImage(String image) {
        assert username
        assert password
        assert image

        if( tokenCache.containsKey(image) )
            return tokenCache[image]

        final basic = "$username:$password".bytes.encodeBase64()
        final auth = "Basic $basic"
        final login = "https://$authUrl?service=$service&scope=repository:${image}:pull"

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
