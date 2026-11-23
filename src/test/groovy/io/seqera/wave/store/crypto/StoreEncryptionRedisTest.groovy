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

package io.seqera.wave.store.crypto

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import io.micronaut.context.ApplicationContext
import io.seqera.fixtures.redis.RedisTestContainer
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import redis.clients.jedis.JedisPool

/**
 * Covers the acceptance scenario: with Redis inspected directly, no usable Platform
 * credential can be read out of a stored record.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Timeout(60)
class StoreEncryptionRedisTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run(
                ['wave.store.encryption.keys': ['a-very-secret-key']],
                'test', 'redis' )
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should not leave a readable bearer or refresh token in redis' () {
        given:
        def store = applicationContext.getBean(JwtAuthStore)
        def pool = applicationContext.getBean(JedisPool)
        def auth = JwtAuth.of('http://tower.io', 'the-access-token', 'the-refresh-token')

        when:
        store.store(auth)

        then: 'the raw redis value carries neither token'
        def raw = pool.resource.withCloseable { it.get("tower-jwt-store/v1:${auth.key}".toString()) }
        raw != null
        !raw.contains('the-access-token')
        !raw.contains('the-refresh-token')
        raw.contains('enc:')

        and: 'but the store still reads them back'
        with(store.get(auth.key)) {
            bearer == 'the-access-token'
            refresh == 'the-refresh-token'
        }
    }

    def 'should still read a record written before encryption was switched on' () {
        given:
        def store = applicationContext.getBean(JwtAuthStore)
        def pool = applicationContext.getBean(JedisPool)
        and: 'a cleartext record, as a pre-rollout Wave would have written it'
        def legacy = '{"key":"legacy-key","endpoint":"http://tower.io","bearer":"cleartext-access","refresh":"cleartext-refresh"}'

        when:
        pool.resource.withCloseable { it.set('tower-jwt-store/v1:legacy-key', legacy) }

        then:
        with(store.get('legacy-key')) {
            bearer == 'cleartext-access'
            refresh == 'cleartext-refresh'
        }
    }

}
