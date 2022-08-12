package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.model.ContentType
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.ContainerTokenService
import io.seqera.wave.service.builder.BuildStatus
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.storage.MemoryStorage
import io.seqera.wave.test.DockerRegistryContainer
import io.seqera.wave.util.Base32
import jakarta.inject.Inject

/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
@MicronautTest(environments = ['test', 'mysql', 'build'])
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CustomImageControllerTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject
    MemoryStorage storage

    int count = 0
    int attemps = 0
    BuildStatus completion

    @MockBean(ContainerBuildService)
    ContainerBuildService containerBuildService(){
        Mock(ContainerBuildServiceImpl){
            isUnderConstruction(_) >> {
                println "IsUnderConstruction count $count, max attemps $attemps"
                ++count < attemps ? BuildStatus.IN_PROGRESS : completion
            }
        }
    }

    @MockBean(ContainerTokenService)
    ContainerTokenService containerTokenService(){
        Mock(ContainerTokenService){
            getRequest(_) >> new ContainerRequestData(
                    null,
                    null,
                    "${Base32.encode('library/hello-world'.bytes)}",
                    "FROM busybox",
                    null,
                    null)
        }
    }

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should wait for head manifest'() {
        given:
        count = 0
        attemps = 3
        completion = BuildStatus.FAILED

        when:
        HttpRequest request = HttpRequest.HEAD("/v2/wt/1234/${Base32.encode('library/hello-world'.bytes)}/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        thrown(HttpClientResponseException)

        and:
        attemps == count
    }

    void 'should stop waiting after a while'() {
        given:
        count = 0
        attemps = 300
        completion = BuildStatus.FAILED

        when:
        HttpRequest request = HttpRequest.HEAD("/v2/wt/1234/${Base32.encode('library/hello-world'.bytes)}/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        thrown(HttpClientResponseException)

        and:
        attemps > count
    }

}
