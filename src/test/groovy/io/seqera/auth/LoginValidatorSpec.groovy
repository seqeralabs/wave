package io.seqera.auth

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.Mock
import io.seqera.testcontainers.SecureDockerRegistryContainer
import io.seqera.docker.ContainerService
import jakarta.inject.Inject

@MicronautTest
class LoginValidatorSpec extends Specification implements SecureDockerRegistryContainer {

    @Inject
    @Shared
    ApplicationContext applicationContext

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'test valid login'() {
        given:
        LoginValidator authProvider = new LoginValidator()

        String uri = REGISTRY_URL ?: registryURL

        when:
        boolean logged = authProvider.login(USER, PWD, uri)

        then:
        logged == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | null                           | true
        'nope'           | 'yepes'         | null                           | false
        Mock.DOCKER_USER | Mock.DOCKER_PAT | "https://registry-1.docker.io" | true
        'nope'           | 'yepes'         | "https://registry-1.docker.io" | false
        Mock.QUAY_USER   | Mock.QUAY_PAT   | "https://quay.io"              | true
        'nope'           | 'yepes'         | "https://quay.io"              | false
    }

    @Inject
    ContainerService containerService

    void 'test containerService valid login'() {
        given:
        String uri = REGISTRY_URL ?: registryURL

        when:
        boolean logged = containerService.validateUser(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | null                           | true
        'nope'           | 'yepes'         | null                           | false
        Mock.DOCKER_USER | Mock.DOCKER_PAT | "https://registry-1.docker.io" | true
        'nope'           | 'yepes'         | "https://registry-1.docker.io" | false
        Mock.QUAY_USER   | Mock.QUAY_PAT   | "https://quay.io"              | true
        'nope'           | 'yepes'         | "https://quay.io"              | false
        'test'           | 'test'          | 'test'                         | true
        'test'           | 'test'          | 'test'                         | true
    }

}
