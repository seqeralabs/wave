package io.seqera

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.seqera.docker.LoginResponse
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MicronautClientTest extends Specification{

    def 'should call target blob' () {
        given:
        def username = "pditommaso"
        def IMAGE = 'library/hello-world'
        def DIGEST = 'sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412'
        def pat = 'd213e955-3357-4612-8c48-fa5652ad968b'
        def client = HttpClient.create(new URL('https://auth.docker.io'))
        def login = "/token?service=registry.docker.io&scope=repository:${IMAGE}:pull"
        def basic = "$username:$pat".bytes.encodeBase64()
        def auth = "Basic $basic"
        
        when:
        HttpRequest request = HttpRequest.create(HttpMethod.GET, login)
                .header("Authorization", auth.toString())
        HttpResponse<LoginResponse> resp = client.toBlocking().exchange(request, LoginResponse);
        println resp.body()
        then:
        resp.status() == HttpStatus.OK
        resp.body().access_token

        when:
        def token = resp.body().token
        def registry = HttpClient.create(new URL(' https://registry-1.docker.io'))
        HttpRequest req1 = HttpRequest.create(HttpMethod.GET, "/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412")
                    .header("Authorization", "Bearer ${token}")
                    .header('Accept-Encoding', 'identity')
//                    .accept("application/json")
//                    .accept("application/vnd.docker.distribution.manifest.v2+json")
//                    .accept("application/vnd.docker.distribution.manifest.list.v2+json")
//                    .accept("application/vnd.oci.image.index.v1+json")
//                    .accept("application/vnd.docker.distribution.manifest.v1+prettyjws")
//                    .accept("application/vnd.oci.image.manifest.v1+json")
        HttpResponse resp1 = registry.toBlocking().exchange(req1);
        println resp1
        then:
        resp1.status == HttpStatus.OK
    }
}
