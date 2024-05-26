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

package io.seqera.wave.tower.auth

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JwtAuthTest extends Specification {

    def 'should copy jwt auth' () {
        given:
        def now = Instant.now();
        and:
        def auth = new JwtAuth(
                'key-12345',
                'http://foo.com',
                'bearer-12345',
                'refresh-12345',
                now,
                now )

        when:
        def auth1 = auth.withKey('key-098765')
        then:
        auth1 != auth
        and:
        auth1.key == 'key-098765'
        auth1.endpoint == auth.endpoint
        auth1.bearer == auth.bearer
        auth1.refresh == auth.refresh
        auth1.createdAt == auth.createdAt
        auth1.updatedAt == auth.updatedAt

        when:
        def auth2 = auth.withBearer('new-bearer')
        then:
        auth2 != auth
        and:
        auth2.key == auth.key
        auth2.endpoint == auth.endpoint
        auth2.bearer == 'new-bearer'
        auth2.refresh == auth.refresh
        auth2.createdAt == auth.createdAt
        auth2.updatedAt == auth.updatedAt

        when:
        def auth3 = auth.withRefresh('new-refresh')
        then:
        auth3 != auth
        and:
        auth3.key == auth.key
        auth3.endpoint == auth.endpoint
        auth3.bearer == auth.bearer
        auth3.refresh == 'new-refresh'
        auth3.createdAt == auth.createdAt
        auth3.updatedAt == auth.updatedAt

        when:
        def t1 = Instant.now()
        def auth4 = auth.withCreatedAt(t1)
        then:
        auth4 != auth
        and:
        auth4.key == auth.key
        auth4.endpoint == auth.endpoint
        auth4.bearer == auth.bearer
        auth4.refresh == auth.refresh
        auth4.createdAt == t1
        auth4.updatedAt == auth.updatedAt

        when:
        def t2 = Instant.now()
        def auth5 = auth.withUpdatedAt(t2)
        then:
        auth5 != auth
        and:
        auth5.key == auth.key
        auth5.endpoint == auth.endpoint
        auth5.bearer == auth.bearer
        auth5.refresh == auth.refresh
        auth5.createdAt == auth.createdAt
        auth5.updatedAt == t2
    }

    def 'should create auth key' () {
        given:
        def endpoint = 'http://foo.com'
        def token = '12345'
        and:
        def KEY = 'fd76d447889fe70c013cbc532ff72a40'
        
        expect:
        JwtAuth.key(endpoint,token) == KEY
        and:
        JwtAuth.of(endpoint,token).key == KEY
        and:
        JwtAuth.of(PlatformId.of(Mock(User), new SubmitContainerTokenRequest(towerEndpoint: endpoint, towerAccessToken: token))).key == KEY
        and:
        JwtAuth.of(new ContainerInspectRequest(towerEndpoint: endpoint, towerAccessToken: token)).key == KEY
        and:
        JwtAuth.of(new SubmitContainerTokenRequest(towerEndpoint: endpoint, towerAccessToken: token)).key == KEY
    }
}
