package io.seqera.wave

import spock.lang.Requires

import groovy.json.JsonSlurper
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JavaClientTest extends Specification{

    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should call target blob' () {
        given:
        def username = System.getenv('DOCKER_USER')
        def IMAGE = 'library/hello-world'
        def DIGEST = 'sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412'
        def pat = System.getenv('DOCKER_PAT')
        def basic = "$username:$pat".bytes.encodeBase64()
        def auth = "Basic $basic"
        and:
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()

        when:
        HttpRequest req0 = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://auth.docker.io/token?service=registry.docker.io&scope=repository:${IMAGE}:pull"))
                .setHeader("Authorization", auth.toString()) // add resp0 header
                .build()

        HttpResponse<String> resp0 = httpClient.send(req0, HttpResponse.BodyHandlers.ofString());
        and:
        def json = (Map) new JsonSlurper().parseText(resp0.body())
        println json
        then:
        resp0.statusCode() == 200
        json.token != null

        when:
        def req1 = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412"))
                .setHeader("Authorization", "Bearer ${json.token}") // add resp0 header
                .build()
        and:
        HttpResponse<String> resp1 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
        then:
        resp1.statusCode() == 200

    }
}
