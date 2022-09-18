package io.seqera.wave.auth

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.auth.RegistryLookupServiceImpl
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryLookupServiceTest extends Specification {

    @Inject RegistryLookupServiceImpl service

    def 'should find registry realm' () {
        given:
        RegistryInfo info

        when:
        info = service.lookup('docker.io')
        then:
        info.name == 'docker.io'
        info.host == new URI('https://registry-1.docker.io')
        info.index == 'https://index.docker.io/v1/'
        info.auth == new RegistryAuth(new URI('https://auth.docker.io/token'),'registry.docker.io', RegistryAuth.Type.Bearer)

        when:
        info = service.lookup('quay.io')
        then:
        info.name == 'quay.io'
        info.host == new URI('https://quay.io')
        info.index == 'https://quay.io'
        info.auth == new RegistryAuth(new URI('https://quay.io/v2/auth'),'quay.io', RegistryAuth.Type.Bearer)

        when:
        info = service.lookup('195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        then:
        info.name == '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        info.host == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        info.index == 'https://195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        info.auth == new RegistryAuth(new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com/'), 'ecr.amazonaws.com', RegistryAuth.Type.Basic)
    }

    def 'should normalize registry url' () {

        expect:
        service.registryEndpoint(REGISTRY) == new URI(EXPECTED)

        where:
        REGISTRY            | EXPECTED
        null                | 'https://registry-1.docker.io/v2/'
        'docker.io'         | 'https://registry-1.docker.io/v2/'
        'quay.io'           | 'https://quay.io/v2/'
        'http://foo.com'    | 'http://foo.com/v2/'
        'http://foo.com/v2' | 'http://foo.com/v2/'
        'http://foo.com/v2/'| 'http://foo.com/v2/'
    }

}
