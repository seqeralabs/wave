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

package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.core.ManifestResult
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegistryProxyControllerSociTest extends Specification {

    def 'should cache a manifest with a pending soci index for the pending cache duration' () {
        given:
        def storage = Mock(Storage)
        def proxy = Mock(RegistryProxyService)
        def config = new SociConfig(pendingCacheDuration: Duration.ofSeconds(30))
        def controller = new RegistryProxyController(storage: storage, proxyService: proxy, sociConfig: config)
        def route = RoutePath.v2path('manifests', 'docker.io', 'library/ubuntu', 'latest', ContainerRequest.of([soci: true]), 'tok123')
        def request = Mock(HttpRequest) {
            getPath() >> '/v2/wt/tok123/library/ubuntu/manifests/latest'
            getHeaders() >> Mock(HttpHeaders) { asMap() >> [:] }
        }
        def store = Mock(DigestStore)

        when:
        def result = controller.manifestForPath(route, request)
        then:
        1 * storage.getManifest('/v2/wt/tok123/library/ubuntu/manifests/latest') >> Optional.empty()
        1 * proxy.resolveManifest(route, [:]) >> new ManifestResult(store, PENDING)
        CACHE_TTL * storage.saveManifest('/v2/wt/tok123/library/ubuntu/manifests/latest', store, Duration.ofSeconds(30))
        CACHE_DEFAULT * storage.saveManifest('/v2/wt/tok123/library/ubuntu/manifests/latest', store)
        result == store

        where:
        PENDING | CACHE_TTL | CACHE_DEFAULT
        true    | 1         | 0
        false   | 0         | 1
    }
}
