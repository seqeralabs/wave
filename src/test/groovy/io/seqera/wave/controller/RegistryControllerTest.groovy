package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.model.ContentType
import io.seqera.wave.storage.MemoryStorage
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ["test", "h2"])
class RegistryControllerTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject
    MemoryStorage storage

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
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525

        when:
        storage.clearCache()

        and:
        response = client.toBlocking().exchange(request,String)

        then:
        response.status() == HttpStatus.OK
        and:
        response.body().indexOf('"schemaVersion":') != -1
        response.getContentType().get().getName() ==  'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525
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
        response.getHeaders().get('Content-Type') == 'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525
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
        response.getHeaders().get('Content-Type') == 'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525

        when:
        storage.clearCache()
        and:
        response = client.toBlocking().exchange(request,String)

        then:
        response.status() == HttpStatus.OK
        and:
        response.getHeaders().get('docker-content-digest').startsWith( 'sha256:')
        response.getHeaders().get('Content-Type') == 'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525
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
        storage.clearCache()
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

}
