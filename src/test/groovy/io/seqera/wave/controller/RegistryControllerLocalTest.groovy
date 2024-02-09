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

import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.model.ContentType
import io.seqera.wave.storage.ManifestCacheStore
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryControllerLocalTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject
    ManifestCacheStore storage

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should get manifest'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body().indexOf('"schemaVersion":') != -1
        response.getContentType().get().getName() ==  'application/vnd.oci.image.index.v1+json'
        response.getContentLength() == 9125

        when:
        storage.clear()

        and:
        response = client.toBlocking().exchange(request,String)

        then:
        response.status() == HttpStatus.OK
        and:
        response.body().indexOf('"schemaVersion":') != -1
        response.getContentType().get().getName() ==  'application/vnd.oci.image.index.v1+json'
        response.getContentLength() == 9125
    }

    void 'should head manifest'() {
        when:
        HttpRequest request = HttpRequest.HEAD("/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        response.headers.each {println "$it.key=$it.value"}
        and:
        response.getHeaders().get('docker-content-digest').startsWith( 'sha256:')
        response.getHeaders().get('Content-Type') == 'application/vnd.oci.image.index.v1+json'
        response.getContentLength() == 9125
    }

    void 'should head manifest and get blob of image'() {
        when:
        HttpRequest request = HttpRequest.HEAD("/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.getHeaders().get('docker-content-digest').startsWith( 'sha256:')
        response.getHeaders().get('Content-Type') == 'application/vnd.oci.image.index.v1+json'
        response.getContentLength() == 9125

        when:
        storage.clear()
        and:
        response = client.toBlocking().exchange(request,String)

        then:
        response.status() == HttpStatus.OK
        and:
        response.getHeaders().get('docker-content-digest').startsWith( 'sha256:')
        response.getHeaders().get('Content-Type') == 'application/vnd.oci.image.index.v1+json'
        response.getContentLength() == 9125
    }

    // Double download hello-world requesting all required layers refreshing cache between them
    void 'should resolve a full request'() {
        given:
        def accept = [
                'application/json',
                'application/vnd.oci.image.index.v1+json', 'application/vnd.docker.distribution.manifest.v1+prettyjws',
                'application/vnd.oci.image.manifest.v1+json', 'application/vnd.docker.distribution.manifest.v2+json',
                'application/vnd.docker.distribution.manifest.list.v2+json'
        ]

        when:
        HttpRequest request = HttpRequest.GET("/v2/$IMAGE/manifests/latest").headers({ h ->
            accept.each {
                h.add('Accept', it)
            }
        })
        HttpResponse<Map> response = client.toBlocking().exchange(request, Map)

        then:
        response.status() == HttpStatus.OK

        when:
        def list = response.body().manifests.collect{
            String type = it.mediaType.indexOf("manifest") ? "manifests" : "blobs"
            "/v2/$IMAGE/$type/$it.digest"
        }
        boolean fails = list.find{ url ->
            HttpRequest requestGet = HttpRequest.GET(url).headers({ h ->
                accept.each {
                    h.add('Accept', it)
                }
            })
            HttpResponse<String> responseGet = client.toBlocking().exchange(requestGet, String)
            responseGet.status() != HttpStatus.OK
        }
        then:
        !fails

        when:
        storage.clear()
        and:
        response = client.toBlocking().exchange(request, String)
        then:
        response.status() == HttpStatus.OK

        when:
        fails = list.find{ url ->
            HttpRequest requestGet = HttpRequest.GET(url).headers({ h ->
                accept.each {
                    h.add('Accept', it)
                }
            })
            HttpResponse<String> responseGet = client.toBlocking().exchange(requestGet, String)
            responseGet.status() != HttpStatus.OK
        }
        then:
        !fails

        where:
        IMAGE | _
        "library/hello-world" | _
    }

    void 'should resolve a tag list'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/tags/list").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        response.headers.each {println "$it.key=$it.value"}
        and:
        new JsonSlurper().parseText(response.body()).name
        new JsonSlurper().parseText(response.body()).tags.size()
    }
}
