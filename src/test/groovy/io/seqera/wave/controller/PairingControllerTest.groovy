/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exchange.PairingResponse
import io.seqera.wave.tower.client.ServiceInfoResponse
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject

@MicronautTest(environments = ['test'])
class PairingControllerTest extends Specification{

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject
    TowerClient towerClient

    @MockBean(TowerClient)
    TowerClient tower() {
        Mock(TowerClient) {
            serviceInfo(_) >> {
                def info = new ServiceInfoResponse(serviceInfo: new ServiceInfoResponse.ServiceInfo(version: '1.0'))
                CompletableFuture.completedFuture(info)
            }
        }
    }

    def 'should perform pairing request'() {
        when: 'doing a proper request'
        def params = [endpoint: 'http://some.tower.com/api', service: 'tower']
        def request = HttpRequest.POST("/pairing",params)
        def res = client.toBlocking().exchange(request, PairingResponse)

        then: 'a public key and keyId is returned'
        res.status() == HttpStatus.OK
        res.body().publicKey
        res.body().pairingId
    }

    @Unroll
    def 'should fail to register with invalid body'() {
        when: 'doing a request with invalid body'
        def request = HttpRequest.POST("/pairing", BODY)
        client.toBlocking().exchange(request, PairingResponse)

        then: 'a bad request is returned'
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        where: 'body has invalid or missing properties'
        BODY                               | _
        [:]                                | _
        [endpoint: 'endpoint']             | _
        [service: '', towerEndpoint: '']   | _
        [:]                                 | _
    }
}
