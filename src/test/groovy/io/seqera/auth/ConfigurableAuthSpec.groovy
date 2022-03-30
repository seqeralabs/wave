package io.seqera.auth

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.Mock
import io.seqera.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class ConfigurableAuthSpec extends Specification implements SecureDockerRegistryContainer{

    @Inject
    @Shared
    ApplicationContext applicationContext

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'test login'(){
        given:
        ConfigurableAuthProvider authProvider = ConfigurableAuthProvider.builder()
                .username("test")
                .password("test")
                .build()
        when:
        String token = authProvider.login(registryURL)
        then:
        token
    }

}
