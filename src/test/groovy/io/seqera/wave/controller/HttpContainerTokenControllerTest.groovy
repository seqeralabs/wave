package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
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
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.exchange.RegisterInstanceResponse
import io.seqera.wave.service.security.KeyRecord
import io.seqera.wave.service.security.SecurityService
import io.seqera.wave.service.security.SecurityServiceImpl
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.UserInfoResponse
import jakarta.inject.Inject

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class HttpContainerTokenControllerTest extends Specification {

    @Requires(property = 'spec.name', value = 'HttpContainerTokenController')
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

    int port

    def setup() {
        port = SocketUtils.findAvailableTcpPort()
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'HttpContainerTokenController',
                'micronaut.server.port': port,
                'tower.api.endpoint':"http://localhost:${port}",
                'micronaut.http.services.default.url' : "http://localhost:$port".toString(),
        ], 'test', 'h2','tower')
    }

    ApplicationContext getApplicationContext() {
        embeddedServer.applicationContext
    }

    def 'should create build request for anonymous user' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()
    }

    def 'should create build request for user 1' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerAccessToken: "1",
                        towerEndpoint: "localhost:${port}",
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()
    }

    def 'should fails build request for user foo' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerAccessToken: 'foo',
                        towerEndpoint: "localhost:${port}",
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        and:
        client
                .toBlocking()
                .exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)
                .body()

        then:
        def err = thrown(HttpClientResponseException)
        and:
        err.status == HttpStatus.UNAUTHORIZED
    }
}
