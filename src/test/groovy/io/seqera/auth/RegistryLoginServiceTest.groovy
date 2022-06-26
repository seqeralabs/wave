package io.seqera.auth

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class RegistryLoginServiceTest extends Specification implements SecureDockerRegistryContainer {

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Shared
    @Value('${wave.registries.docker.username}')
    String dockerUsername

    @Shared
    @Value('${wave.registries.docker.password}')
    String dockerPassword

    @Shared
    @Value('${wave.registries.quay.username}')
    String quayUsername

    @Shared
    @Value('${wave.registries.quay.password}')
    String quayPassword

    @Inject RegistryLoginService loginService

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'test valid login'() {
        given:

        String uri = REGISTRY_URL ?: registryURL

        when:
        boolean logged = loginService.login(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | null                           | true
        'nope'           | 'yepes'         | null                           | false
        dockerUsername   | dockerPassword  | "https://registry-1.docker.io" | true
        'nope'           | 'yepes'         | "https://registry-1.docker.io" | false
        quayUsername     | quayPassword     | "https://quay.io"              | true
        'nope'           | 'yepes'         | "https://quay.io"              | false
    }


    void 'test containerService valid login'() {
        given:
        String uri = REGISTRY_URL ?: getRegistryURL()

        when:
        boolean logged = loginService.validateUser(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | null                           | true
        'nope'           | 'yepes'         | null                           | false
        dockerUsername   | dockerPassword  | "https://registry-1.docker.io" | true
        'nope'           | 'yepes'         | "https://registry-1.docker.io" | false
        quayUsername     | quayPassword    | "https://quay.io"              | true
        'nope'           | 'yepes'         | "https://quay.io"              | false
    }


}
