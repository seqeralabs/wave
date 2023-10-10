/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
