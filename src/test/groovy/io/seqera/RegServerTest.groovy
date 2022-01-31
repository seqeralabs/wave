package io.seqera

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegServerTest extends Specification {


    def 'should handle ping get' () {
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

    def 'should handle ping head' () {
        given:
        def handler = new RegHandler()
        def server = new RegServer().withHandler(handler).start()
        and:
        def client = HttpClient.create(new URL('http://localhost:9090'))

        when:
        HttpRequest request = HttpRequest.HEAD("/ping");
        def response = client.toBlocking().exchange(request);
        then:
        response.status() == HttpStatus.OK
        response.contentLength == 4
        
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
