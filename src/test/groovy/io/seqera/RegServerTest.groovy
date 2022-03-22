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
