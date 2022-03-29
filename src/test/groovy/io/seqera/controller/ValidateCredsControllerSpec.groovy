package io.seqera.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.config.DefaultConfiguration
import io.seqera.model.ContentType
import jakarta.inject.Inject
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@MicronautTest
class ValidateCredsControllerSpec extends Specification{

    @Shared
    static GenericContainer testcontainers = new GenericContainer(DockerImageName.parse("registry:2.2"))
            .withExposedPorts(5000)
            .withPrivilegedMode(true)
            .withCopyFileToContainer(MountableFile.forClasspathResource ("/registry.password"),"/auth/")
            .withEnv(REGISTRY_AUTH: "htpasswd",
                    REGISTRY_AUTH_HTPASSWD_REALM: "Registry",
                    REGISTRY_AUTH_HTPASSWD_PATH: "/auth/registry.password")
            .waitingFor(
                    Wait.forLogMessage(".*listening on .*\\n", 1)
            );

    String getRegistryURL(){
        int port = testcontainers.firstMappedPort
        String url = "$testcontainers.containerIpAddress:$port"
        url
    }

    void initRegistryContainer(ApplicationContext applicationContext){
        testcontainers.start()
    }

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should validate username required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds",[
                password:'test',
        ])
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate pwd required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds",[
                userName:'test',
        ])
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate the test user'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds",[
                userName:'test',
                password: 'test'
        ])
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body() == "Ok"
    }


}
