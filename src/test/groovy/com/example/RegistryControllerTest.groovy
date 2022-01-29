package com.example


import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client;

    void 'should get manifest'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/latest");
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body().startsWith('{"manifests":')
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.list.v2+json'
        response.getContentLength() == 2562
    }

    void 'should head manifest'() {
        when:
        HttpRequest request = HttpRequest.HEAD("/v2/library/hello-world/manifests/latest");
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.getHeaders().get('docker-content-digest') == 'sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800'
        response.getHeaders().get('Content-Type') == 'application/vnd.docker.distribution.manifest.list.v2+json'
//        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.list.v2+json'
//        response.getContentLength() == 2562
    }
}
