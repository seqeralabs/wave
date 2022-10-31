package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
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
import io.seqera.wave.storage.RedisStorage
import io.seqera.wave.test.DockerRegistryContainer

import jakarta.inject.Inject
import redis.clients.jedis.Jedis
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
@MicronautTest(environments = ["test", "h2", "redis"])
@Property(name='wave.build.timeout',value = '3s')
class RedisRegistryControllerTest extends Specification implements DockerRegistryContainer{

    @Inject
    ApplicationContext applicationContext

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

    void 'should get manifest'() {
        given:
        HttpClient client = applicationContext.createBean(HttpClient, applicationContext.getBean(EmbeddedServer).URL)
        RedisStorage storage = applicationContext.getBean(RedisStorage)

        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/latest").headers({h->
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
        storage.clearCache()

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
        HttpClient client = applicationContext.createBean(HttpClient, applicationContext.getBean(EmbeddedServer).URL)
        and:
        jedis.set("wave-tokens/v1:1234", '{"containerImage":"hello-world"}')
        jedis.set("wave-build/v1:hello-world", '{"containerImage":"hello-world"}')
        when:
        HttpRequest request = HttpRequest.GET("/v2/wt/1234/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        client.toBlocking().exchange(request,String)
        then:
        final exception = thrown(HttpClientResponseException)
        RegistryErrorResponse error = exception.response.getBody(RegistryErrorResponse).get()
        error.errors.get(0).message.contains('Build of container \'hello-world\' timed out')
    }
}
