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

package io.seqera.wave.core

import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryProxyServiceTest extends Specification {

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject RegistryProxyService registryProxyService


    def 'should retrieve image digest' () {
        given:
        def IMAGE = 'library/hello-world@sha256:6352af1ab4ba4b138648f8ee88e63331aae519946d3b67dae50c313c6fc8200f'
        def request = Mock(BuildRequest)

        when:
        def resp1 = registryProxyService.getImageDigest(request)
        then:
        request.getTargetImage() >> IMAGE
        then:
        resp1 == 'sha256:6352af1ab4ba4b138648f8ee88e63331aae519946d3b67dae50c313c6fc8200f'
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should retrieve image digest on ECR' () {
        given:
        def IMAGE = '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/app:1.0.0'
        def request = Mock(BuildRequest)

        when:
        def resp1 = registryProxyService.getImageDigest(request)
        then:
        request.getTargetImage() >> IMAGE
        request.getIdentity() >> new PlatformId()
        then:
        resp1 == 'sha256:d5c169e210d6b789b2dc7eced66471cf4ce2c7260ac7299fbef464ff902086be'
    }

    def 'should return a stable hash' () {
        given:
        def p1 = RoutePath.parse('quay.io/v2/ubuntu/blobs/sha256:123')

        when:
        def k1 = RegistryProxyService.requestKey(p1,  null)
        then:
        k1 == 'a7e1706bf0024934'

        when:
        def k2 = RegistryProxyService.requestKey(p1, [:])
        then:
        k2 == 'a7e1706bf0024934'

        when:
        def k3 = RegistryProxyService.requestKey(p1, ['Content-Type': ['text/1']])
        then:
        k3 == '3a2d860dc46a1bc4'

        when:
        def k4 = RegistryProxyService.requestKey(p1, ['Content-Type': ['text/1', 'text/2']])
        then:
        k4 == 'f9fe81aed4d72cba'

        when:
        def k5 = RegistryProxyService.requestKey(p1, ['Content-Type': ['text/1', 'text/2'], 'X-Foo': ['bar']])
        then:
        k5 == 'f9fe81aed4d72cba'    // <-- the header 'X-Foo' does not alter the cache key
    }

    def 'check is cacheable header' () {
        expect:
        RegistryProxyService.isCacheableHeader(KEY) == EXPECTED
        where:
        KEY             | EXPECTED
        null            | false
        ''              | false
        'Foo'           | false
        'accept'        | true
        'Accept'        | true
        'Content-Type'  | true
    }

}
