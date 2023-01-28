package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exchange.RegistryErrorResponse
import io.seqera.wave.model.ContentType
import io.seqera.wave.storage.ManifestCacheStore
import io.seqera.wave.test.DockerRegistryContainer
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.Jedis
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
@MicronautTest
class RegistryControllerRedisTest extends Specification implements DockerRegistryContainer, RedisTestContainer{

    EmbeddedServer embeddedServer

    int port

    Jedis jedis

    def setup() {
        port = SocketUtils.findAvailableTcpPort()
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
                REDIS_HOST   : redisHostName,
                REDIS_PORT   : redisPort,
                'wave.build.timeout':'3s',
                'micronaut.server.port': port,
                'micronaut.http.services.default.url' : "http://localhost:$port".toString(),
        ], 'test', 'h2', 'redis')

        jedis = new Jedis(redisHostName, redisPort as int)
        jedis.flushAll()
        initRegistryContainer(applicationContext)
    }

    def cleanup(){
        jedis.close()
    }

    ApplicationContext getApplicationContext() {
        embeddedServer.applicationContext
    }

    void 'should get manifest'() {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)
        ManifestCacheStore storage = applicationContext.getBean(ManifestCacheStore)

        when:
        HttpRequest request = HttpRequest.GET("http://localhost:$port/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body().indexOf('"schemaVersion":') != -1
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525

        when:
        storage.clear()

        and:
        response = client.toBlocking().exchange(request,String)

        then:
        response.status() == HttpStatus.OK
        and:
        response.body().indexOf('"schemaVersion":') != -1
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525
    }

    @Timeout(10)
    void 'should render a timeout when build failed'() {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)
        and:
        jedis.set("wave-tokens/v1:1234", '{"containerImage":"library/hello-world"}')
        jedis.set("wave-build/v1:library/hello-world", '{"containerImage":"library/hello-world"}')
        when:
        HttpRequest request = HttpRequest.GET("http://localhost:$port/v2/wt/1234/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        client.toBlocking().exchange(request,String)
        then:
        final exception = thrown(HttpClientResponseException)
        RegistryErrorResponse error = exception.response.getBody(RegistryErrorResponse).get()
        error.errors.get(0).message.contains('Build of container \'library/hello-world\' timed out')
    }
}
