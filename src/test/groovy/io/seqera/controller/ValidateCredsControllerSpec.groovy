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
import io.seqera.Mock
import io.seqera.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class ValidateCredsControllerSpec extends Specification implements SecureDockerRegistryContainer {

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
        HttpRequest request = HttpRequest.POST("/validate-creds", [
                password: 'test',
        ])
        client.toBlocking().exchange(request, Boolean)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate pwd required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds", [
                userName: 'test',
        ])
        client.toBlocking().exchange(request, Boolean)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate the test user'() {
        given:
        HttpRequest request = HttpRequest.POST("/validate-creds", [
                userName: 'test',
                password: 'test',
                registry: 'test'
        ])
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body()
    }

    void 'test validateController valid login'() {
        given:
        HttpRequest request = HttpRequest.POST("/validate-creds", [
                userName: USER,
                password: PWD,
                registry: REGISTRY_URL
        ])
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)

        then:
        response.status() == HttpStatus.OK
        and:
        response.body() == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | 'test'                         | true
        'nope'           | 'yepes'         | 'test'                         | false
        Mock.DOCKER_USER | Mock.DOCKER_PAT | "https://registry-1.docker.io" | true
        'nope'           | 'yepes'         | "https://registry-1.docker.io" | false
        Mock.QUAY_USER   | Mock.QUAY_PAT   | "https://quay.io"              | true
        'nope'           | 'yepes'         | "https://quay.io"              | false
        'test'           | 'test'          | 'test'                         | true
    }
}
