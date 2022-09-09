package io.seqera.wave.ratelimit

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
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

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should check rate limit in head manifest'() {
        when:
        HttpRequest request = HttpRequest.HEAD("/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        (0..configuration.request.max).each {
            client.toBlocking().exchange(request, String)
        }
        then:
        thrown(HttpClientResponseException)
    }

}
