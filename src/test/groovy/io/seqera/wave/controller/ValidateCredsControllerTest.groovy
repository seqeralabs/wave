package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.test.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class ValidateCredsControllerTest extends Specification implements SecureDockerRegistryContainer {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Shared
    @Value('${wave.registries.docker.io.username}')
    String dockerUsername

    @Shared
    @Value('${wave.registries.docker.io.password}')
    String dockerPassword

    @Shared
    @Value('${wave.registries.quay.io.username}')
    String quayUsername

    @Shared
    @Value('${wave.registries.quay.io.password}')
    String quayPassword


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
        def req = [
                userName:'test',
                password:'test',
                registry: getTestRegistryUrl('test') ]
        and:
        HttpRequest request = HttpRequest.POST("/validate-creds", req)
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body()
    }

    void 'test validateController valid login'() {
        given:
        def req = [
                userName: USER,
                password: PWD,
                registry: getTestRegistryUrl(REGISTRY_URL)
        ]
        HttpRequest request = HttpRequest.POST("/validate-creds", req)
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
        dockerUsername   | dockerPassword  | "https://registry-1.docker.io" | true
        'nope'           | 'yepes'         | "https://registry-1.docker.io" | false
        quayUsername     | quayPassword    | "https://quay.io"              | true
        'nope'           | 'yepes'         | "https://quay.io"              | false
        'test'           | 'test'          | 'test'                         | true
    }
}
