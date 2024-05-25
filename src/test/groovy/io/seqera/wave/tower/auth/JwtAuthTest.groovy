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
                'http://foo.com',
                'token-12345',
                'bearer-12345',
                'refresh-12345',
                now )

        expect:
        auth.key() == 'jwt-ddc895f371f1bdd9b0be104ce093a61b'

        when:
        def auth2 = auth.withBearer('new-bearer')
        then:
        auth2 != auth
        and:
        auth2.key() == auth.key()
        auth2.endpoint == auth.endpoint
        auth2.token == auth.token
        auth2.bearer == 'new-bearer'
        auth2.refresh == auth.refresh
        auth2.expiration == auth.expiration

        when:
        def auth3 = auth.withRefresh('new-refresh')
        then:
        auth3 != auth
        and:
        auth3.key() == auth.key()
        auth3.endpoint == auth.endpoint
        auth3.token == auth.token
        auth3.bearer == auth.bearer
        auth3.refresh == 'new-refresh'
        auth3.expiration == auth.expiration

    }
}
