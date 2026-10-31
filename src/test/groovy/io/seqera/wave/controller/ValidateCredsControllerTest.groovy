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

package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.test.SecureDockerRegistryContainer
import jakarta.inject.Inject

@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'false')
class ValidateCredsControllerTest extends Specification implements SecureDockerRegistryContainer {

    @Inject
    @Client("/")
    HttpClient client;

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


    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    private String testRegistryName(String registry) {
        // validation rejects a scheme, and a bare localhost registry is reached over http
        return registry == 'test' ? "localhost:${testcontainers.firstMappedPort}" : registry
    }

    void 'should validate username required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds", [request: [
                password: 'test',
        ]])
        client.toBlocking().exchange(request, Boolean)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate pwd required'() {
        when:
        HttpRequest request = HttpRequest.POST("/validate-creds", [request:[
                userName: 'test',
        ]])
        client.toBlocking().exchange(request, Boolean)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate the test user'() {
        given:
        def req = [request: [
                userName:'test',
                password:'test',
                registry: testRegistryName('test') ]]
        and:
        HttpRequest request = HttpRequest.POST("/validate-creds", req)
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body()
    }

    void 'test validateController valid login'() {
        given:
        def req = [request: [
                userName: USER,
                password: PWD,
                registry: testRegistryName(REGISTRY_URL)
        ]]
        HttpRequest request = HttpRequest.POST("/validate-creds", req)
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)

        then:
        response.status() == HttpStatus.OK
        and:
        response.body() == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | 'test'                         | true
        'nope'           | 'yepes'         | 'test'                         | false
        dockerUsername   | dockerPassword  | "docker.io"                    | true
        'nope'           | 'yepes'         | "docker.io"                    | false
        quayUsername     | quayPassword    | "quay.io"                      | true
        'nope'           | 'yepes'         | "quay.io"                      | false
        'test'           | 'test'          | 'test'                         | true
    }

    void 'should validate username required'() {
        when:
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", [
                password: 'test',
        ])
        client.toBlocking().exchange(request, Boolean)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate pwd required'() {
        when:
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", [
                userName: 'test',
        ])
        client.toBlocking().exchange(request, Boolean)
        then:
        def e = thrown(HttpClientResponseException)
    }

    void 'should validate the test user'() {
        given:
        def req = [
                userName:'test',
                password:'test',
                registry: testRegistryName('test') ]
        and:
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body()
    }

    void 'test validateController valid login'() {
        given:
        def req = [
                userName: USER,
                password: PWD,
                registry: testRegistryName(REGISTRY_URL)
        ]
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)
        when:
        HttpResponse<Boolean> response = client.toBlocking().exchange(request, Boolean)

        then:
        response.status() == HttpStatus.OK
        and:
        response.body() == VALID

        where:
        USER             | PWD             | REGISTRY_URL                   | VALID
        'test'           | 'test'          | 'test'                         | true
        'nope'           | 'yepes'         | 'test'                         | false
        dockerUsername   | dockerPassword  | "docker.io"                    | true
        'nope'           | 'yepes'         | "docker.io"                    | false
        quayUsername     | quayPassword    | "quay.io"                      | true
        'nope'           | 'yepes'         | "quay.io"                      | false
        'test'           | 'test'          | 'test'                         | true
    }

}
