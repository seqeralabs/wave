package io.seqera.wave.auth

import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.test.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
class RegistryAuthServiceTest extends Specification implements SecureDockerRegistryContainer {

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Shared
    @Value('${wave.registries.docker.io.username}')
    String dockerUsername
    @Shared
    @Value('${wave.registries.docker.io.password}')
    String dockerPassword

    @Shared
    @Value('${wave.registries.quay.io.username}')
    String quayUsername
    @Shared
    @Value('${wave.registries.quay.io.password}')
    String quayPassword

    @Shared
    @Value('${wave.registries.seqeralabs.azurecr.io.username}')
    String azureUsername
    @Shared
    @Value('${wave.registries.seqeralabs.azurecr.io.password}')
    String azurePassword

    @Inject
    RegistryAuthService loginService

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'test valid login'() {
        given:

        String uri = getTestRegistryUrl(REGISTRY_URL)

        when:
        boolean logged = loginService.login(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER           | PWD            | REGISTRY_URL                   | VALID
        'test'         | 'test'         | 'localhost'                    | true
        'nope'         | 'yepes'        | 'localhost'                    | false
        dockerUsername | dockerPassword | "https://registry-1.docker.io" | true
        'nope'         | 'yepes'        | "https://registry-1.docker.io" | false
        quayUsername   | quayPassword   | "https://quay.io"              | true
        'nope'         | 'yepes'        | "https://quay.io"              | false
    }

    @IgnoreIf({!System.getenv('AZURECR_USER')})
    void 'test valid azure login'() {
        given:

        String uri = getTestRegistryUrl(REGISTRY_URL)

        when:
        boolean logged = loginService.login(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER           | PWD            | REGISTRY_URL                   | VALID
        azureUsername  | azurePassword  | 'seqeralabs.azurecr.io'        | true
    }


    void 'test containerService valid login'() {
        given:
        String uri = getTestRegistryUrl(REGISTRY_URL)

        when:
        boolean logged = loginService.validateUser(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER           | PWD            | REGISTRY_URL                   | VALID
        'test'         | 'test'         | 'localhost'                    | true
        'nope'         | 'yepes'        | 'localhost'                    | false
        dockerUsername | dockerPassword | "https://registry-1.docker.io" | true
        'nope'         | 'yepes'        | "https://registry-1.docker.io" | false
        quayUsername   | quayPassword   | "https://quay.io"              | true
        'nope'         | 'yepes'        | "https://quay.io"              | false
    }

    void 'test buildLoginUrl'() {
        given:
        RegistryAuthServiceImpl impl = loginService as RegistryAuthServiceImpl

        when:
        def url = impl.buildLoginUrl(new URI(REALM), IMAGE, SERVICE)

        then:
        url == EXPECTED

        where:
        REALM       | IMAGE  | SERVICE   | EXPECTED
        'localhost' | 'test' | 'service' | "localhost?scope=repository:test:pull&service=service"
        'localhost' | 'test' | null      | "localhost?scope=repository:test:pull"
    }

}
