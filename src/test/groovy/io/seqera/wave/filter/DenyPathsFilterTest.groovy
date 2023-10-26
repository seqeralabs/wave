/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exchange.DescribeWaveContainerResponse
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@MicronautTest(environments = 'test-deny-paths')
class DenyPathsFilterTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    DenyPathsFilter denyPathsFilter

    def "should deny path, if its present in wave.denyPaths configuration"(){
        when:
        def req = HttpRequest.GET("/container-token/token1")
        client.toBlocking().exchange(req, DescribeWaveContainerResponse)

        then:
        final exception = thrown(HttpClientResponseException)
        exception.status.code == 405
    }

    def "should return true if the path needs to be denied"() {
        given:
        def deniedPaths =['/v2/wt/token1/wave/build/manifest-1',
                                        '/v2/wt/token2/wave/build/manifest-2']
        expect:
        RESULT == denyPathsFilter.isDeniedPath(PATH, deniedPaths)

        where:
        RESULT | PATH
        true   | '/v2/wt/token1/wave/build/manifest-1'
        false  | '/v2/wt/token3/wave/build/manifest-3'
        true   | '/v2/wt/token2/wave/build/manifest-2'
        false  | '/v2/wt/token4/wave/build/manifest-4'
    }
}
