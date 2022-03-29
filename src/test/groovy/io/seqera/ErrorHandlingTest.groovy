package io.seqera

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.config.DefaultConfiguration
import io.seqera.config.Registry
import io.seqera.config.RegistryBean
import io.seqera.model.ContentType
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ErrorHandlingTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ApplicationContext applicationContext

    def setup() {
        applicationContext.getBeansOfType(RegistryBean)*.name=null
    }

    void 'should handle an error'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<JsonError> response = client.toBlocking().exchange(request,JsonError)
        then:
        final exception = thrown(HttpClientResponseException)
        JsonError error = exception.response.getBody(JsonError).get()
        error.message.startsWith("Error: ")
        error.links.size()==1
    }
}
