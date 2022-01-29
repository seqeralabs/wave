package io.seqera

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegServerTest extends Specification {


    def 'should handle ping' () {
        given:
        def handler = new RegHandler()
        def server = new RegServer().withHandler(handler).start()
        and:
        def client = HttpClient.create(new URL('http://localhost:9090'))

        when:
        HttpRequest<String> request = HttpRequest.GET("/ping");
        def response = client.toBlocking().retrieve(request);
        then:
        response == 'pong'

        cleanup:
        server.stop()
    }

    def 'should handle manifest list get request' () {
        given:
        def handler = new RegHandler()
        def server = new RegServer().withHandler(handler).start()
        and:
        def client = HttpClient.create(new URL('http://localhost:9090'))

        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/latest");
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body() == Mock.MANIFEST_LIST_CONTENT
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.list.v2+json'
        response.getContentLength() == 2562

        cleanup:
        server.stop()
    }

    def 'should handle manifest list head request' () {
        given:
        def handler = new RegHandler()
        def server = new RegServer().withHandler(handler).start()
        and:
        def client = HttpClient.create(new URL('http://localhost:9090'))

        when:
        HttpRequest request = HttpRequest.HEAD("/v2/library/hello-world/manifests/latest");
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.list.v2+json'
        response.getContentLength() == 2562

        cleanup:
        server.stop()
    }

    def 'should handle unknown' () {
        given:
        def handler = new RegHandler()
        def server = new RegServer().withHandler(handler).start()
        and:
        def client = HttpClient.create(new URL('http://localhost:9090'))

        when:
        HttpRequest<String> request = HttpRequest.GET("/foo");
        client.toBlocking().exchange(request);
        then:
        def e = thrown(HttpClientResponseException)
        and:
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        server.stop()
    }
}
