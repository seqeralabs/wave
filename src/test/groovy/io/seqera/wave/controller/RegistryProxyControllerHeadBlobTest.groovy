/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.proxy.DelegateResponse
import io.seqera.wave.storage.DockerDigestStore
import io.seqera.wave.storage.HttpDigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.storage.ZippedDigestStore

/**
 * Unit tests for blob {@code HEAD} handling in {@link RegistryProxyController}
 */
class RegistryProxyControllerHeadBlobTest extends Specification {

    static final String DIGEST = 'sha256:6d1ef012b5674ad8a127ecfa9b5e6f5178d171b90ee462846974177fd9bdd39f'

    Storage storage = Mock(Storage)
    RegistryProxyService proxyService = Mock(RegistryProxyService)
    RegistryProxyController controller

    def setup() {
        controller = new RegistryProxyController()
        controller.@storage = storage
        controller.@proxyService = proxyService
    }

    def 'should answer blob HEAD from the cache without reading the content'() {
        given:
        def route = RoutePath.parse("docker.io/v2/library/foo/blobs/$DIGEST")
        def entry = Spy(ZippedDigestStore.fromUncompressed('hello'.bytes, 'application/octet-stream', DIGEST, 5))

        when:
        def resp = controller.handleHead(route, HttpRequest.HEAD("/v2/library/foo/blobs/$DIGEST"))

        then:
        1 * storage.getBlob(route.targetPath) >> Optional.of(entry)
        0 * entry.getBytes()
        0 * proxyService._
        and:
        resp.status() == HttpStatus.OK
        resp.header('Content-Length') == '5'
        resp.header('docker-content-digest') == DIGEST
        !resp.body.isPresent()
    }

    def 'should answer blob HEAD of an http cached entry with its size'() {
        given:
        def route = RoutePath.parse("docker.io/v2/library/foo/blobs/$DIGEST")
        def entry = new HttpDigestStore('https://fusionfs.seqera.io/layer.tar.gz', 'application/vnd.docker.image.rootfs.diff.tar.gzip', DIGEST, 1000)

        when:
        def resp = controller.handleHead(route, HttpRequest.HEAD("/v2/library/foo/blobs/$DIGEST"))

        then:
        1 * storage.getBlob(route.targetPath) >> Optional.of(entry)
        0 * proxyService._
        and:
        resp.status() == HttpStatus.OK
        resp.header('Content-Length') == '1000'
        resp.header('docker-content-digest') == DIGEST
    }

    def 'should forward blob HEAD of a docker cached entry to the mapped location'() {
        given:
        def route = RoutePath.parse("docker.io/v2/library/foo/blobs/$DIGEST")
        def entry = new DockerDigestStore("docker://quay.io/v2/bar/blobs/$DIGEST", 'application/octet-stream', DIGEST, 1000)

        when:
        def resp = controller.handleHead(route, HttpRequest.HEAD("/v2/library/foo/blobs/$DIGEST"))

        then:
        1 * storage.getBlob(route.targetPath) >> Optional.of(entry)
        1 * proxyService.handleHead({ RoutePath it -> it.targetPath == "quay.io/v2/bar/blobs/$DIGEST" }, _) >>
                new DelegateResponse(statusCode: 200, headers: ['Content-Length': ['1000'], 'docker-content-digest': [DIGEST]])
        and:
        resp.status() == HttpStatus.OK
        resp.header('docker-content-digest') == DIGEST
    }

    def 'should return the resolved location when the registry redirects a blob HEAD'() {
        given:
        def route = RoutePath.parse("docker.io/v2/library/foo/blobs/$DIGEST")

        when:
        def resp = controller.handleHead(route, HttpRequest.HEAD("/v2/library/foo/blobs/$DIGEST"))

        then:
        1 * storage.getBlob(route.targetPath) >> Optional.empty()
        1 * proxyService.handleHead(route, _) >> new DelegateResponse(
                statusCode: 307,
                location: 'https://registry.example.com/storage/blob?sig=xyz',
                headers: ['Location': ['/storage/blob?sig=xyz'], 'Content-Length': ['1000']])
        and:
        resp.status() == HttpStatus.TEMPORARY_REDIRECT
        resp.header('Location') == 'https://registry.example.com/storage/blob?sig=xyz'
        resp.header('Content-Length') == '0'
    }

    def 'should return the registry status when the blob does not exist'() {
        given:
        def route = RoutePath.parse("docker.io/v2/library/foo/blobs/$DIGEST")

        when:
        def resp = controller.handleHead(route, HttpRequest.HEAD("/v2/library/foo/blobs/$DIGEST"))

        then:
        1 * storage.getBlob(route.targetPath) >> Optional.empty()
        1 * proxyService.handleHead(route, _) >> new DelegateResponse(statusCode: 404, headers: [:])
        and:
        resp.status() == HttpStatus.NOT_FOUND
    }
}
