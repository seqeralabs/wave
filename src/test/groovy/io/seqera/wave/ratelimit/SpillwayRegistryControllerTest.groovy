package io.seqera.wave.ratelimit

import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.model.ContentType
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['test', 'rate-limit'])
class SpillwayRegistryControllerTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject
    RateLimiterConfig configuration

    @MockBean(HttpClientAddressResolver)
    HttpClientAddressResolver addressResolver(){
        final AtomicInteger counter = new AtomicInteger()
        Mock(HttpClientAddressResolver){
            resolve(_) >> {
                counter.incrementAndGet() % 2 == 0 ? "127.0.0.1" : "10.0.0.1"
            }
        }
    }

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should check rate limit in ip of anonymous manifest'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        (0..configuration.pull.anonymous.max).each {
            client.toBlocking().exchange(request, String)
        }
        then:
        true

        when:
        (0..configuration.pull.anonymous.max*2).each {
            client.toBlocking().exchange(request, String)
        }

        then:
        def e = thrown(HttpClientResponseException)
        e.message == 'Too Many Requests'
        def b = new JsonSlurper().parseText( e.response.body.get() as String)
        b.errors.size()
        b.errors.first().code == 'DENIED'
        b.errors.first().message.contains('Request exceeded pull rate limit for IP')
    }

}
