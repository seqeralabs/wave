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
import spock.lang.Unroll

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.model.ContentType
import io.seqera.wave.proxy.DelegateResponse
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.storage.ZippedDigestStore
/**
 * Test the manifest resolution logic of {@link RegistryProxyController}
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RegistryProxyControllerTest extends Specification {

    static final String TOKEN = '12345'
    static final String IMAGE = 'library/htslib_samtools'
    static final String TAG = '1.23.1--5b6bb4ede7e612e5'
    static final String AUGMENTED = 'sha256:add5c021ca3d5e8cc0b1a5e1b7e2a4f6c8d9e0b1c2d3e4f5a6b7c8d9e0a1b2c3'
    static final String REGISTRY = 'community.wave.seqera.io'
    static final String TARGET_PATH = "$REGISTRY/v2/$IMAGE/manifests/$AUGMENTED"

    static ContainerRequest augmentedRequest(Map opts=[:]) {
        ContainerRequest.of([
                containerImage: "$REGISTRY/$IMAGE:$TAG".toString(),
                containerConfig: new ContainerConfig(layers: [new ContainerLayer('https://host.com/layer.tar.gz', 'sha256:aaa', 10, 'sha256:bbb')])
        ] + opts)
    }

    static RoutePath digestRoute(ContainerRequest request, String token=TOKEN) {
        RoutePath.v2path('manifests', REGISTRY, IMAGE, AUGMENTED, request, token)
    }

    static DigestStore manifestOf(String digest=AUGMENTED) {
        ZippedDigestStore.fromUncompressed('{"schemaVersion":2}'.bytes, ContentType.DOCKER_MANIFEST_V2_TYPE, digest, 19)
    }

    static HttpRequest someGetRequest() {
        HttpRequest.GET("/v2/wt/$TOKEN/$IMAGE/manifests/$AUGMENTED")
    }

    static HttpRequest someHeadRequest() {
        HttpRequest.HEAD("/v2/wt/$TOKEN/$IMAGE/manifests/$AUGMENTED")
    }

    // note: instance fields, not @Shared - a shared mock would leak interaction counts across features
    def storage = Mock(Storage)
    def proxyService = Mock(RegistryProxyService)
    def controller = new RegistryProxyController(storage: storage, proxyService: proxyService)

    def 'should rebuild the augmented manifest when the by-digest request misses the cache'() {
        given:
        def route = digestRoute(augmentedRequest())
        def manifest = manifestOf()

        when:
        def resp = controller.handleGet0(route, someGetRequest())

        then: 'the cache misses, then the rebuilt entry is read back'
        2 * storage.getManifest(TARGET_PATH) >>> [Optional.empty(), Optional.of(manifest)]
        and: 'the augmentation is re-run against the *tag* carried by the container request'
        1 * proxyService.handleManifest({ RoutePath it ->
            it.manifest && it.reference == TAG && it.image == IMAGE && it.registry == REGISTRY && it.token == TOKEN
        }, _) >> manifest
        and: 'the upstream registry is never asked for the augmented digest'
        0 * proxyService.handleRequest(_, _)
        and:
        resp.status() == HttpStatus.OK
        resp.header('docker-content-digest') == AUGMENTED
    }

    def 'should rebuild the augmented manifest on a HEAD request'() {
        given:
        def route = digestRoute(augmentedRequest())
        def manifest = manifestOf()

        when:
        def resp = controller.handleHead(route, someHeadRequest())

        then:
        2 * storage.getManifest(TARGET_PATH) >>> [Optional.empty(), Optional.of(manifest)]
        and: 'the tag - not the augmented digest - is resolved upstream'
        1 * proxyService.handleManifest({ RoutePath it -> it.reference == TAG }, _) >> manifest
        and:
        resp.status() == HttpStatus.OK
        resp.header('docker-content-digest') == AUGMENTED
    }

    def 'should serve a manifest nested in the rebuilt image index'() {
        given: 'a multi-platform container: the rebuild resolves the tag to the index, not to the requested manifest'
        def route = digestRoute(augmentedRequest())
        def index = manifestOf('sha256:1111111111111111111111111111111111111111111111111111111111111111')
        def nested = manifestOf()

        when:
        def resp = controller.handleGet0(route, someGetRequest())

        then: 'what the rebuild returns is ignored - the requested manifest is read back from storage'
        2 * storage.getManifest(TARGET_PATH) >>> [Optional.empty(), Optional.of(nested)]
        and:
        1 * proxyService.handleManifest(_, _) >> index
        and:
        0 * proxyService.handleRequest(_, _)
        and:
        resp.status() == HttpStatus.OK
        resp.header('docker-content-digest') == AUGMENTED
    }

    def 'should not loop when the rebuild does not yield the requested digest'() {
        given: 'the upstream tag has moved, so the augmentation produces a different digest'
        def route = digestRoute(augmentedRequest())

        when:
        def resp = controller.handleGet0(route, someGetRequest())

        then:
        2 * storage.getManifest(TARGET_PATH) >> Optional.empty()
        and:
        1 * proxyService.handleManifest(_, _) >> null
        and: 'it falls through to the upstream registry exactly once'
        1 * proxyService.handleRequest(route, _) >> new DelegateResponse(statusCode: 404, headers: [:], body: 'MANIFEST_UNKNOWN'.bytes)
        and:
        resp.status() == HttpStatus.NOT_FOUND
    }

    @Unroll
    def 'should not rebuild #scenario'() {
        when:
        controller.handleGet0(route, someGetRequest())

        then:
        1 * storage.getManifest(TARGET_PATH) >> Optional.empty()
        and: 'the request is forwarded upstream as before'
        0 * proxyService.handleManifest(_, _)
        1 * proxyService.handleRequest(route, _) >> new DelegateResponse(statusCode: 200, headers: [:], body: 'ok'.bytes)

        where:
        scenario                                  | route
        'a route carrying no wave token'          | RoutePath.v2path('manifests', REGISTRY, IMAGE, AUGMENTED)
        'a request that was never augmented'      | digestRoute(ContainerRequest.of(containerImage: "$REGISTRY/$IMAGE:$TAG".toString()))
    }

    def 'should fall back when the container request is pinned by digest'() {
        given: 'a container pinned by digest is never augmented - the augmenter rejects the rebuild'
        def route = digestRoute(augmentedRequest(containerImage: "$REGISTRY/$IMAGE@$AUGMENTED".toString()))

        when:
        def resp = controller.handleGet0(route, someGetRequest())

        then:
        1 * storage.getManifest(TARGET_PATH) >> Optional.empty()
        and:
        1 * proxyService.handleManifest({ RoutePath it -> it.reference == AUGMENTED }, _) >> {
            throw new DockerRegistryException('Unsupported container reference', 400, 'UNSUPPORTED') }
        and: 'the request is forwarded upstream as before'
        1 * proxyService.handleRequest(route, _) >> new DelegateResponse(statusCode: 200, headers: [:], body: 'ok'.bytes)
        and:
        resp.status() == HttpStatus.OK
    }

    def 'should fall back to the upstream registry when the rebuild fails'() {
        given:
        def route = digestRoute(augmentedRequest())

        when:
        def resp = controller.handleGet0(route, someGetRequest())

        then:
        1 * storage.getManifest(TARGET_PATH) >> Optional.empty()
        and:
        1 * proxyService.handleManifest(_, _) >> { throw new RuntimeException('Upstream is down') }
        and: 'the failure is contained - behaviour is no worse than before the rebuild existed'
        1 * proxyService.handleRequest(route, _) >> new DelegateResponse(statusCode: 404, headers: [:], body: 'MANIFEST_UNKNOWN'.bytes)
        and:
        resp.status() == HttpStatus.NOT_FOUND
    }
}
