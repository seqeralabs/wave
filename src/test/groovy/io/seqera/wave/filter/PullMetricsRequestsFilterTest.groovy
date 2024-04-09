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

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
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
                containerImage: 'hello-world:sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6', containerConfig: cfg)
        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("container-token", req), SubmitContainerTokenResponse)
        and:
        def request = HttpRequest.GET("/v2/wt/${resp.body().containerToken}" +
                "/library/hello-world/manifests/sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6")
        HttpResponse<String> response = httpClient.toBlocking().exchange(request,String)
        and:
        req = HttpRequest.GET("/v1alpha2/metrics/pulls?date=$date").basicAuth("username", "password")
        def res = httpClient.toBlocking().exchange(req, Map)

        then:
        res.body() == [count: 1]
        res.status.code == 200
    }
}