package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.ContainerInspectResponse
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.logs.BuildLogServiceImpl
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerInspectControllerTest extends Specification {

    @MockBean(BuildLogServiceImpl)
    BuildLogService logsService() {
        Mock(BuildLogService)
    }

    @Inject
    @Client("/")
    HttpClient client

    def 'should inspect container' () {
        when:
        def inspect = new ContainerInspectRequest(containerImage: 'busybox')
        def req = HttpRequest.POST("/v1alpha1/inspect", inspect)
        def resp = client.toBlocking().exchange(req, ContainerInspectResponse)
        then:
        resp.status() == HttpStatus.OK
        and:
        resp.body().container.registry == 'docker.io'
        resp.body().container.imageName == 'library/busybox'
        resp.body().container.reference == 'latest'
        resp.body().container.digest == 'sha256:6d9ac9237a84afe1516540f40a0fafdc86859b2141954b4d643af7066d598b74'
        resp.body().container.config.architecture == 'amd64'
        resp.body().container.manifest.schemaVersion == 2
        resp.body().container.manifest.mediaType == 'application/vnd.oci.image.manifest.v1+json'
        resp.body().container.manifest.config.mediaType == 'application/vnd.oci.image.config.v1+json'
        resp.body().container.manifest.config.digest == 'sha256:3f57d9401f8d42f986df300f0c69192fc41da28ccc8d797829467780db3dd741'
        resp.body().container.manifest.config.size == 581
        resp.body().container.manifest.layers[0].mediaType == 'application/vnd.oci.image.layer.v1.tar+gzip'
        resp.body().container.manifest.layers[0].digest == 'sha256:9ad63333ebc97e32b987ae66aa3cff81300e4c2e6d2f2395cef8a3ae18b249fe'
        resp.body().container.manifest.layers[0].size == 2220094
        resp.body().container.config.rootfs.type == 'layers'
        resp.body().container.config.rootfs.diff_ids == ['sha256:2e112031b4b923a873c8b3d685d48037e4d5ccd967b658743d93a6e56c3064b9']
    }

}
