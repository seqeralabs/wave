package io.seqera.wave

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

    def 'should call target blob' () {
        given:
        def username = "pditommaso"
        def IMAGE = 'library/hello-world'
        def DIGEST = 'sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412'
        def pat = 'd213e955-3357-4612-8c48-fa5652ad968b'
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
        println resp1.body()
        then:
        resp1.statusCode() == 200

//        when:
//        def token = resp.body().token
//        def registry = HttpClient.create(new URL(' https://registry-1.docker.io'))
//        HttpRequest req1 = HttpRequest.create(HttpMethod.GET, "/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412")
//                    .header("Authorization", "Bearer ${token}")
//                    .header('Accept-Encoding', 'identity')
////                    .accept("application/json")
////                    .accept("application/vnd.docker.distribution.manifest.v2+json")
////                    .accept("application/vnd.docker.distribution.manifest.list.v2+json")
////                    .accept("application/vnd.oci.image.index.v1+json")
////                    .accept("application/vnd.docker.distribution.manifest.v1+prettyjws")
////                    .accept("application/vnd.oci.image.manifest.v1+json")
//        HttpResponse resp1 = registry.toBlocking().exchange(req1);
//        println resp1
//        then:
//        resp1.status == HttpStatus.OK
    }
}
