/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import spock.lang.Specification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryLookupServiceTest extends Specification {

    @Inject
    RegistryLookupServiceImpl service

    def 'should find registry realm' () {
        given:
        RegistryInfo info

        when:
        info = service.lookup('docker.io')
        then:
        info.name == 'docker.io'
        info.host == new URI('https://registry-1.docker.io')
        info.indexHost == 'https://index.docker.io/v1/'
        info.auth == new RegistryAuth(new URI('https://auth.docker.io/token'),'registry.docker.io', RegistryAuth.Type.Bearer)

        when:
        info = service.lookup('quay.io')
        then:
        info.name == 'quay.io'
        info.host == new URI('https://quay.io')
        info.indexHost == 'https://quay.io'
        info.auth == new RegistryAuth(new URI('https://quay.io/v2/auth'),'quay.io', RegistryAuth.Type.Bearer)

        when:
        info = service.lookup('195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        then:
        info.name == '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        info.host == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        info.indexHost == 'https://195996028523.dkr.ecr.eu-west-1.amazonaws.com'
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

    def 'should fall back to the endpoint when a Basic challenge has a non-URL realm' () {
        given:
        def server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
        server.createContext('/v2/', { HttpExchange exchange ->
            exchange.responseHeaders.add('WWW-Authenticate', CHALLENGE)
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        })
        server.start()
        def endpoint = new URI("http://localhost:${server.address.port}/v2/")

        when:
        def auth = service.lookup0(endpoint)

        then:
        auth == new RegistryAuth(endpoint, null, RegistryAuth.Type.Basic)

        cleanup:
        server?.stop(0)

        where:
        CHALLENGE << [
                'Basic realm="Registry Realm"',
                // Sonatype Nexus Repository Cloud
                'BASIC realm="User-Token-Realm"',
                'basic realm="foo"',
        ]
    }

}
