/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.v1.model.ValidateRegistryCredsRequest
import io.seqera.wave.auth.RegistryAuthService
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'false')
class CredentialsV1ControllerSpec extends Specification {

    @Inject @Client('/') HttpClient client
    @Inject RegistryAuthService registryAuthService

    @MockBean(RegistryAuthService)
    RegistryAuthService mockRegistryAuthService() { Mock(RegistryAuthService) }

    def 'POST /w1/credentials/validate delegates to RegistryAuthService and returns true'() {
        given:
        def req = new ValidateRegistryCredsRequest()
                .userName('me')
                .password('secret')
                .registry('docker.io')
        registryAuthService.validateUser('docker.io', 'me', 'secret') >> true

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.POST('/w1/credentials/validate', req), Boolean)

        then:
        resp.status == HttpStatus.OK
        resp.body() == true
    }

    def 'POST /w1/credentials/validate returns false when credentials are invalid'() {
        given:
        def req = new ValidateRegistryCredsRequest()
                .userName('bad')
                .password('wrong')
                .registry('docker.io')
        registryAuthService.validateUser('docker.io', 'bad', 'wrong') >> false

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.POST('/w1/credentials/validate', req), Boolean)

        then:
        resp.status == HttpStatus.OK
        resp.body() == false
    }

    def 'POST /w1/credentials/validate fails when userName is missing'() {
        given:
        def body = [password: 'secret', registry: 'docker.io']

        when:
        client.toBlocking()
                .exchange(HttpRequest.POST('/w1/credentials/validate', body), Boolean)

        then:
        thrown(HttpClientResponseException)
    }

    def 'POST /w1/credentials/validate fails when password is missing'() {
        given:
        def body = [userName: 'me', registry: 'docker.io']

        when:
        client.toBlocking()
                .exchange(HttpRequest.POST('/w1/credentials/validate', body), Boolean)

        then:
        thrown(HttpClientResponseException)
    }

    def 'POST /w1/credentials/validate fails when registry is missing'() {
        given:
        def body = [userName: 'me', password: 'secret']

        when:
        client.toBlocking()
                .exchange(HttpRequest.POST('/w1/credentials/validate', body), Boolean)

        then:
        thrown(HttpClientResponseException)
    }
}
