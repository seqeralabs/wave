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

package test

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.seqera.wave.redis.RedisFactory
import jakarta.inject.Singleton
import redis.clients.jedis.JedisPool
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
class RedisTestFactory {

    @Primary
    @Singleton
    @Replaces(RedisFactory)
    JedisPool createRedisPool() {
        // those properties are set in the RedisTestContainer class
        final h = System.getProperty("redis.host")
        final p = Integer.parseInt(System.getProperty("redis.port"))
        return new JedisPool(h, p)
    }

}
