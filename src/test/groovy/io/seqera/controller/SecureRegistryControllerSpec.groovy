package io.seqera.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.testcontainers.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class SecureRegistryControllerSpec extends Specification implements SecureDockerRegistryContainer{

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }


    // Empty by the moment
    void 'test'(){
        expect:
        true
    }

}
