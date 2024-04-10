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

package io.seqera.wave.service.metric

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.Jedis
/**
 * MetricsCounter tests based on Redis
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class MetricsCounterStoreRedisTest  extends Specification implements RedisTestContainer {
    ApplicationContext applicationContext

    Jedis jedis

    def setup() {
        applicationContext = ApplicationContext.run([
                wave:[ build:[ timeout: '5s' ]],
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        jedis = new Jedis(redisHostName, redisPort as int)
        jedis.flushAll()
    }

    def cleanup(){
        jedis.close()
    }
    
    def 'should get correct count value' () {
        given:
        def metricsCounterStore = applicationContext.getBean(MetricsCounterStore)

        when:
        metricsCounterStore.inc('foo')
        metricsCounterStore.inc('foo')
        metricsCounterStore.inc('bar')

        then:
        metricsCounterStore.get('foo') == 2
        metricsCounterStore.get('bar') == 1
    }
}
