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

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MockBean
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.service.pairing.PairingRecord
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.UserInfoResponse
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
class ContainerTokenControllerHttpTest extends Specification {

    @MockBean
    @Primary
    PairingService getPairingService(){
        return Stub(PairingService.class)
    }

    @Requires(property = 'spec.name', value = 'TowerController')
    @Controller("/")
    static class TowerController {
        @Get('/user-info')
        HttpResponse<UserInfoResponse> userInfo(@Header("Authorization") String authorization) {
            if( authorization == 'Bearer foo')
                return HttpResponse.unauthorized()
            HttpResponse.ok(new UserInfoResponse(user: new User(id:1)))
        }
    }

    EmbeddedServer embeddedServer

    ApplicationContext applicationContext

    int port

    def setup() {
        port = SocketUtils.findAvailableTcpPort()
        def props = [
                'spec.name': 'TowerController',
                'micronaut.server.port': port,
                'tower.api.endpoint':"http://localhost:${port}",
                'micronaut.http.services.default.url' : "http://localhost:$port".toString() ]
        embeddedServer = ApplicationContext.run(EmbeddedServer, props, 'test')
        embeddedServer.applicationContext.registerSingleton(getPairingService())
        applicationContext = embeddedServer.applicationContext
    }

    def 'should create build request for anonymous user' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def resp = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
    }

    def 'should create build request for user 1' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)
        def pairingService = applicationContext.getBean(PairingService)
        def pairingRecord = [service: "tower", endpoint: "http://localhost:${port}", expiration: Instant.now() + Duration.ofSeconds(5)]
        and:
        pairingService.getPairingRecord("tower", _) >> new PairingRecord(pairingRecord)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerAccessToken: "1",
                        towerRefreshToken: "2",
                        towerEndpoint: "http://localhost:${port}",
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        def resp = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
    }

    def 'should fails build request for user foo' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)
        def pairingService = applicationContext.getBean(PairingService)
        def pairingRecord = [service: "tower", endpoint: "http://localhost:${port}"]
        and:
        pairingService.getPairingRecord("tower", _) >> new PairingRecord(pairingRecord)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerAccessToken: 'foo',
                        towerRefreshToken: 'foo2',
                        towerEndpoint: "http://localhost:${port}",
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        and:
        client
                .toBlocking()
                .exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)
                .body()

        then:
        def err = thrown(HttpClientResponseException)
        and:
        err.status == HttpStatus.NOT_FOUND
    }

    def 'should fail build request if the instance has not registered for key exchange' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)
        def pairingService = applicationContext.getBean(PairingService)
        and:
        pairingService.getPairingRecord("tower",_) >> null

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def request = new SubmitContainerTokenRequest(
                towerAccessToken: 'x',
                towerEndpoint: "localhost:${port}",
                towerWorkspaceId: 10,
                containerImage: 'ubuntu:latest',
                containerConfig: cfg,
                containerPlatform: 'arm64'
        )

        client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token",request), SubmitContainerTokenResponse).body()
        then:
        def err = thrown(HttpClientResponseException)
        and:
        err.status == HttpStatus.BAD_REQUEST
    }

    def 'should create build request' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def resp = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", req), SubmitContainerTokenResponse)

        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken
    }

    def 'should not retrieve an expired build request' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def resp = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", req), SubmitContainerTokenResponse)

        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken
    }

    def 'should retrieve a valid build request' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def resp = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", req), SubmitContainerTokenResponse)

        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken

        when:
        RouteHandler routeHelper = applicationContext.getBean(RouteHandler)
        routeHelper.parse("/v2/wt/${resp.body().containerToken}/library/ubuntu/blobs/latest")

        then:
        noExceptionThrown()
    }

    def 'should validate same image' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def resp = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken

        when:
        RouteHandler routeHelper = applicationContext.getBean(RouteHandler)
        routeHelper.parse("/v2/wt/${resp.body().containerToken}/library/hello/blobs/latest")

        then:
        thrown(IllegalArgumentException)
    }
}
