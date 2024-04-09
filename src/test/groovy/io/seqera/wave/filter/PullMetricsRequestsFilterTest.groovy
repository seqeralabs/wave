/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.filter


import spock.lang.Specification

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.model.ContentType
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.metrics.enabled', value = 'true')
class PullMetricsRequestsFilterTest extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    def 'should increment the pulls metrics'() {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10,
                containerImage: 'hello-world:sha256:e2fc4e5012d16e7fe466f5291c476431beaa1f9b90a5c2125b493ed28e2aba57', containerConfig: cfg)
        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("container-token", req), SubmitContainerTokenResponse)
        and:
        def request = HttpRequest.GET("/v2/wt/${resp.body().containerToken}" +
                "/library/hello-world/manifests/sha256:e2fc4e5012d16e7fe466f5291c476431beaa1f9b90a5c2125b493ed28e2aba57")
                .headers({h-> h.add('Accept', ContentType.OCI_IMAGE_MANIFEST_V1)
        })
        httpClient.toBlocking().exchange(request)
        and:
        req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=$date").basicAuth("username", "password")
        def res = httpClient.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200
    }
}
