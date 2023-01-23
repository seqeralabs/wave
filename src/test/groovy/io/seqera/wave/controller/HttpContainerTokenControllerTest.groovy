package io.seqera.wave.controller

import spock.lang.Specification

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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.service.security.KeyRecord
import io.seqera.wave.service.security.SecurityService
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.UserInfoResponse

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@MicronautTest
class HttpContainerTokenControllerTest extends Specification {


    @MockBean
    @Primary
    SecurityService getSecurityService(){
        return Stub(SecurityService.class)
    }

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
        embeddedServer.applicationContext.registerSingleton(getSecurityService())
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

        and:
        applicationContext.getBean(SecurityService).getServiceRegistration("tower", _) >> new KeyRecord(service: "tower", hostname: "http://localhost:${port}")

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerAccessToken: "1",
                        towerRefreshToken: "2",
                        towerEndpoint: "http://localhost:${port}",
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()
    }

    def 'should fails build request for user foo' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        and:
        applicationContext.getBean(SecurityService).getServiceRegistration("tower", _) >> new KeyRecord(service: "tower", hostname: "http://localhost:${port}")

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
        and:
        applicationContext.getBean(SecurityService).getServiceRegistration("tower",_) >> null
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
}
