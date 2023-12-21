/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.auth

import spock.lang.Ignore
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

    @Shared
    @Value('${wave.registries.195996028523.dkr.ecr.eu-west-1.amazonaws.com.username}')
    String awsEcrUsername
    @Shared
    @Value('${wave.registries.195996028523.dkr.ecr.eu-west-1.amazonaws.com.password}')
    String awsEcrPassword

    @Inject
    RegistryAuthService loginService

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'test valid login'() {
        given:

        String uri = getTestRegistryName(REGISTRY)

        when:
        boolean logged = loginService.login(uri, USER, PWD)

        then:
        logged == VALID

        where:
        USER           | PWD            | REGISTRY          | VALID
        'test'         | 'test'         | 'localhost'       | true
        'nope'         | 'yepes'        | 'localhost'       | false
        dockerUsername | dockerPassword | "docker.io"       | true
        'nope'         | 'yepes'        | "docker.io"       | false
        quayUsername   | quayPassword   | "quay.io"         | true
        'nope'         | 'yepes'        | "quay.io"         | false
    }

    @IgnoreIf({!System.getenv('AZURECR_USER')})
    void 'test valid azure login'() {
        given:
        def REGISTRY = 'seqeralabs.azurecr.io'

        expect:
        loginService.login(REGISTRY, azureUsername, azurePassword)
    }

    @IgnoreIf({!System.getenv('AWS_ACCESS_KEY_ID')})
    void 'test valid aws ecr private'() {
        given:
        String REGISTRY = '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        expect:
        loginService.login(REGISTRY, awsEcrUsername, awsEcrPassword)
    }

    @IgnoreIf({!System.getenv('AWS_ACCESS_KEY_ID')})
    void 'test valid aws ecr public'() {
        given:
        String REGISTRY = 'public.ecr.aws'
        expect:
        loginService.login(REGISTRY, awsEcrUsername, awsEcrPassword)
    }
    
    void 'test containerService valid login'() {
        given:
        String registry = getTestRegistryName(REGISTRY)

        when:
        boolean logged = loginService.validateUser(registry, USER, PWD)

        then:
        logged == VALID

        where:
        USER           | PWD            | REGISTRY                              | VALID
        'test'         | 'test'         | 'localhost'                           | true
        'nope'         | 'yepes'        | 'localhost'                           | false
        dockerUsername | dockerPassword | "docker.io"                           | true
        'nope'         | 'yepes'        | "docker.io"                           | false
        quayUsername   | quayPassword   | "quay.io"                             | true
        'nope'         | 'yepes'        | "quay.io"                             | false
        and:
        dockerUsername | dockerPassword | "docker.io/pditommaso/wave-tests"     | true
        dockerUsername | dockerPassword | "docker.io/pditommaso"                | true
        // the following should fail
        //dockerUsername | dockerPassword | "registry-1.docker.io/unknown/repo"           | false
    }

    @Ignore
    void 'should test gitea login' () {
        given:
        def uri = 'gitea.dev-tower.net'
        def USER = 'bn_user'
        def PWD = 'xyz'

        expect:
        loginService.validateUser(uri, USER, PWD)
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
