package io.seqera

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.text.SimpleDateFormat

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegServerTest extends Specification {

    @Inject
    EmbeddedApplication application

    @Inject
    @Client('/')
    HttpClient client

    def 'should handle ping get' () {
        when:
        HttpRequest<String> request = HttpRequest.GET("/ping");
        def response = client.toBlocking().retrieve(request);
        then:
        response == 'pong'
    }

    //Ignore until next release of micronaut fix head issue
    @IgnoreIf({ new Date().before(new SimpleDateFormat('yyyy/MM/dd').parse('2022/08/01'))})
    def 'should handle ping head' () {
        when:
        HttpRequest request = HttpRequest.HEAD("/ping");
        def response = client.toBlocking().exchange(request);
        then:
        response.status() == HttpStatus.OK
        response.contentLength == 4
    }

    def 'should handle unknown' () {
        when:
        HttpRequest<String> request = HttpRequest.GET("/foo");
        client.toBlocking().exchange(request);
        then:
        def e = thrown(HttpClientResponseException)
        and:
        e.status == HttpStatus.NOT_FOUND
    }
}
