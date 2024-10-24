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

package io.seqera.wave.redis

import spock.lang.Specification

import redis.clients.jedis.exceptions.InvalidURIException
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RedisFactoryTest extends Specification {
    def 'should create redis pool with valid URI'() {
        given:
        def factory = new RedisFactory()

        when:
        def pool = factory.createRedisPool(URI_STRING, MIN_IDLE, MAX_IDLE, MAX_TOTAL, TIMEOUT)

        then:
        pool != null

        where:
        URI_STRING               | MIN_IDLE | MAX_IDLE  | MAX_TOTAL |  TIMEOUT
        'redis://localhost:6379' | 0        | 10        | 50        | 5000
        'rediss://localhost:6379'| 1        | 5         | 20        | 3000
    }

    def 'should throw exception for invalid URI'() {
        given:
        def factory = new RedisFactory()

        when:
        factory.createRedisPool(URI_STRING, MIN_IDLE, MAX_IDLE, MAX_TOTAL, TIMEOUT)

        then:
        def e = thrown(InvalidURIException)
        e.message.contains("Cannot open Redis connection due invalid URI")

        where:
        URI_STRING               | MIN_IDLE | MAX_IDLE  | MAX_TOTAL |  TIMEOUT
        'redis://localhost' | 0        | 10        | 50        | 5000
        'localhost:6379'| 1        | 5         | 20        | 3000
    }

}
