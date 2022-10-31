package io.seqera.wave.controller


import spock.lang.Specification

import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.exception.NotFoundException

import jakarta.inject.Inject
import redis.clients.jedis.Jedis
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@MicronautTest(environments = ["test", "h2", "redis"])
@Property(name='wave.build.timeout',value = '5s')
class RedisContainerTokenControllerTest extends Specification {

    @Inject
    ApplicationContext applicationContext

    @Inject
    @Client("/")
    HttpClient client;

    @Value('${redis.uri}')
    String redisUrl

    Jedis jedis

    def setup() {
        jedis = new Jedis(redisUrl)
        jedis.flushAll()
    }

    def cleanup(){
        jedis.close()
    }

    def 'should create build request' () {
        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()

        and:
        new JsonSlurper().parseText(jedis.get("wave-tokens/v1:"+body.containerToken)).platform.arch == 'arm64'
        new JsonSlurper().parseText(jedis.get("wave-tokens/v1:"+body.containerToken)).workspaceId == 10
        new JsonSlurper().parseText(jedis.get("wave-tokens/v1:"+body.containerToken)).containerImage == 'ubuntu:latest'
    }

    def 'should not retrieve an expired build request' () {

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()

        when:
        jedis.del("wave-tokens/v1:"+body.containerToken)

        and:
        RouteHandler routeHelper = applicationContext.getBean(RouteHandler)
        routeHelper.parse("/v2/wt/$body.containerToken/library/hello-world/blobs/latest")

        then:
        thrown(NotFoundException)
    }

    def 'should retrieve a valid build request' () {
        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()

        when:
        RouteHandler routeHelper = applicationContext.getBean(RouteHandler)
        routeHelper.parse("/v2/wt/$body.containerToken/library/ubuntu/blobs/latest")

        then:
        true
    }

    def 'should validate same image' () {
        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()

        when:
        RouteHandler routeHelper = applicationContext.getBean(RouteHandler)
        routeHelper.parse("/v2/wt/$body.containerToken/library/hello/blobs/latest")

        then:
        thrown(IllegalArgumentException)
    }
}
