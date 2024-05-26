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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class JwtAuthStoreLocalTest extends Specification {

    @Inject
    JwtAuthStore store

    def 'should put and get jwt tokens' () {
        given:
        def now = Instant.now();
        and:
        def auth = new JwtAuth(
                'http://foo.com',
                'bearer-12345',
                'refresh-12345',
                now,
                now )
        when:
        store.store(JwtAuth.key(auth), auth)
        then:
        store.refresh(auth) == store.get(JwtAuth.key(auth))
        and:
        with(store.get(JwtAuth.key(auth))) {
            endpoint == auth.endpoint
            bearer == auth.bearer
            refresh == auth.refresh
            createdAt == auth.createdAt
            updatedAt >= auth.updatedAt
        }
    }
}
