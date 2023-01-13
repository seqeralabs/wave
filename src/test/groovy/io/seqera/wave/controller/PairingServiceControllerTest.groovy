package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exchange.PairServiceResponse
import jakarta.inject.Inject


@MicronautTest(environments = ['test'])
class PairingServiceControllerTest extends Specification{

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    @Shared
    ApplicationContext applicationContext


    def 'should register a service'() {
        when: 'doing a proper request'
        def request = HttpRequest.POST("/pair-service",
            [
                    service: 'tower',
                    endpoint: 'localhost'
            ])
        def res = client.toBlocking().exchange(request, PairServiceResponse)

        then: 'a public key and keyId is returned'
        res.status() == HttpStatus.OK
        res.body().publicKey
        res.body().keyId
    }

    @Unroll
    def 'should fail to register with invalid body'() {
        when: 'doing a request with invalid body'
        def request = HttpRequest.POST("/pair-service", body)
        client.toBlocking().exchange(request,PairServiceResponse)

        then: 'a bad request is returned'
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        where: 'body has invalid or missing properties'

        body                               | _
        [:]                                | _
        [endpoint: 'endpoint']             | _
        [service: '', towerEndpoint: '']   | _
        []                                 | _
    }
}
