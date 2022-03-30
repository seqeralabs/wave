package io.seqera.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class ValidateCredsControllerSpec extends Specification implements SecureDockerRegistryContainer{

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should validate username required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds",[
                password:'test',
        ])
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate pwd required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds",[
                userName:'test',
        ])
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate the test user'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds",[
                userName:'test',
                password: 'test'
        ])
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body() == "Ok"
    }


}
