package io.seqera.wave.auth

import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.auth.RegistryLookupServiceImpl
import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryLookupServiceTest extends Specification {

    @Inject RegistryLookupServiceImpl service

    def 'should find registry realm' () {

        expect:
        service.lookup('docker.io')
                == new RegistryInfo(
                'docker.io',
                        new URI('https://registry-1.docker.io'),
                        new RegistryAuth(new URI('https://auth.docker.io/token'),'registry.docker.io', RegistryAuth.Type.Bearer))
        and:
        service.lookup('quay.io') ==
                new RegistryInfo(
                'quay.io',
                        new URI('https://quay.io'),
                        new RegistryAuth(new URI('https://quay.io/v2/auth'),'quay.io', RegistryAuth.Type.Bearer) )
        and:
        service.lookup('195996028523.dkr.ecr.eu-west-1.amazonaws.com') ==
                new RegistryInfo(
                        '195996028523.dkr.ecr.eu-west-1.amazonaws.com',
                        new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com'),
                        new RegistryAuth(new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com/'), 'ecr.amazonaws.com', RegistryAuth.Type.Basic))
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
