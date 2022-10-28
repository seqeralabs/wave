package io.seqera.wave.security

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
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
import io.seqera.wave.auth.DockerAuthService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContentType
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.service.token.ContainerTokenService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.MemoryStorage
import io.seqera.wave.storage.Storage
import io.seqera.wave.test.DockerRegistryContainer
import io.seqera.wave.util.Base32
import jakarta.inject.Inject


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@MicronautTest(environments = ['test', 'h2', 'build'])
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class TestControllerTest extends Specification{

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(ContainerBuildService)
    ContainerBuildService containerBuildService(){
        Mock(ContainerBuildServiceImpl){
            buildImage(BuildRequest) >> {it.targetImage}
        }
    }
    @MockBean(DockerAuthService)
    DockerAuthService dockerAuthService(){
        Mock(DockerAuthService)
    }

    void 'should fails if no user provided'() {
        when:
        HttpRequest request = HttpRequest.GET("/test-build").basicAuth("root","no pwd")
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        thrown(HttpClientResponseException)
    }

    void 'should works if user provided'() {
        when:
        HttpRequest request = HttpRequest.GET("/test-build").basicAuth("root","Wave!1234")
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status.code == 200
    }

}
