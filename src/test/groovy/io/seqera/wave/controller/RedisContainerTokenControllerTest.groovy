package io.seqera.wave.controller

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.JedisPool


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class RedisContainerTokenControllerTest extends Specification implements RedisTestContainer {

    EmbeddedServer embeddedServer

    int port

    JedisPool jedisPool

    def setup() {
        port = SocketUtils.findAvailableTcpPort()
        restartRedis()
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
                REDIS_HOST   : redisHostName,
                REDIS_PORT   : redisPort,
                'micronaut.server.port': port,
                'micronaut.http.services.default.url' : "http://localhost:$port".toString(),
        ], 'test', 'h2', 'redis')

        jedisPool = new JedisPool(redisHostName, redisPort as int)
    }

    ApplicationContext getApplicationContext() {
        embeddedServer.applicationContext
    }

    def 'should create build request' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()

        and:
        new JsonSlurper().parseText(jedisPool.resource.get("wave/token/"+body.containerToken)).platform.arch == 'arm64'
        new JsonSlurper().parseText(jedisPool.resource.get("wave/token/"+body.containerToken)).workspaceId == 10
        new JsonSlurper().parseText(jedisPool.resource.get("wave/token/"+body.containerToken)).containerImage == 'ubuntu:latest'
    }

    def 'should not retrieve an expired build request' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

        def body = ret.body()

        then:
        noExceptionThrown()

        when:
        jedisPool.resource.del("wave/token/"+body.containerToken)

        and:
        RouteHandler routeHelper = applicationContext.getBean(RouteHandler)
        routeHelper.parse("/v2/wt/$body.containerToken/library/hello-world/blobs/latest")

        then:
        thrown(NotFoundException)
    }

    def 'should retrieve a valid build request' () {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

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
        given:
        HttpClient client = applicationContext.createBean(HttpClient)

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        def ret = client.toBlocking().exchange(HttpRequest.POST("http://localhost:$port/container-token", request), SubmitContainerTokenResponse)

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
