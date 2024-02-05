package io.seqera.wave.auth

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.HttpStatus.UNAUTHORIZED
import static io.micronaut.http.MediaType.TEXT_PLAIN

@MicronautTest
@Property(name="wave.auth.basic.username",value="username")
@Property(name="wave.auth.basic.password",value="password")
class BasicAuthTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "by default every endpoint is secured"() {
        when: 'Accessing a secured URL without authenticating'
        client.toBlocking().exchange(HttpRequest.GET('/').accept(TEXT_PLAIN))

        then: 'returns unauthorized'
        HttpClientResponseException e = thrown()
        e.status == UNAUTHORIZED
        e.response.headers.contains("WWW-Authenticate")
        e.response.headers.get("WWW-Authenticate") == 'Basic realm="Wave Authentication"'
    }

    void "Verify HTTP Basic Auth works"() {
        when: 'A secured URL is accessed with Basic Auth'
        HttpRequest request = HttpRequest.GET('/metrics')
                .basicAuth("username", "password")
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then: 'the endpoint can be accessed'
        rsp.status == OK
    }
}
