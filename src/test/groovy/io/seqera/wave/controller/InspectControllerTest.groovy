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

import spock.lang.Specification

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.ContainerInspectResponse
import io.seqera.wave.service.inspect.model.BlobURIResponse
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.logs.BuildLogServiceImpl
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class InspectControllerTest extends Specification {

    @MockBean(BuildLogServiceImpl)
    BuildLogService logsService() {
        Mock(BuildLogService)
    }

    @Inject
    @Client("/")
    HttpClient client

    def 'should inspect container' () {
        when:
        def inspect = new ContainerInspectRequest(containerImage:
                'library/busybox@sha256:4be429a5fbb2e71ae7958bfa558bc637cf3a61baf40a708cb8fff532b39e52d0')
        def req = HttpRequest.POST("/v1alpha1/inspect", inspect)
        def resp = client.toBlocking().exchange(req, ContainerInspectResponse)
        then:
        resp.status() == HttpStatus.OK
        and:
        resp.body().container.registry == 'docker.io'
        resp.body().container.imageName == 'library/busybox'
        resp.body().container.reference == 'sha256:4be429a5fbb2e71ae7958bfa558bc637cf3a61baf40a708cb8fff532b39e52d0'
        resp.body().container.digest == 'sha256:4be429a5fbb2e71ae7958bfa558bc637cf3a61baf40a708cb8fff532b39e52d0'
        resp.body().container.config.architecture == 'amd64'
        resp.body().container.manifest.schemaVersion == 2
        resp.body().container.manifest.mediaType == 'application/vnd.oci.image.manifest.v1+json'
        resp.body().container.manifest.config.mediaType == 'application/vnd.oci.image.config.v1+json'
        resp.body().container.manifest.config.digest == 'sha256:ba5dc23f65d4cc4a4535bce55cf9e63b068eb02946e3422d3587e8ce803b6aab'
        resp.body().container.manifest.config.size == 372
        resp.body().container.manifest.layers[0].mediaType == 'application/vnd.oci.image.layer.v1.tar+gzip'
        resp.body().container.manifest.layers[0].digest == 'sha256:7b2699543f22d5b8dc8d66a5873eb246767bca37232dee1e7a3b8c9956bceb0c'
        resp.body().container.manifest.layers[0].size == 2152262
        resp.body().container.config.rootfs.type == 'layers'
        resp.body().container.config.rootfs.diff_ids == ['sha256:95c4a60383f7b6eb6f7b8e153a07cd6e896de0476763bef39d0f6cf3400624bd']
    }

    def 'should get list of blob URI' () {
        when:
        def inspect = new ContainerInspectRequest(containerImage:
                'library/busybox@sha256:4be429a5fbb2e71ae7958bfa558bc637cf3a61baf40a708cb8fff532b39e52d0')
        def req = HttpRequest.POST("/v1alpha1/blob/uri", inspect)
        def resp = client.toBlocking().exchange(req, BlobURIResponse)

        then:
        resp.status() == HttpStatus.OK
        and:
        resp.body().uris == ['https://registry-1.docker.io/v2/library/busybox/blobs/sha256:7b2699543f22d5b8dc8d66a5873eb246767bca37232dee1e7a3b8c9956bceb0c']
    }

}
