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

import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JwtTimeRedisTest extends Specification implements RedisTestContainer{

    @Shared
    ApplicationContext applicationContext

    JwtTimeStore timer

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        and:
        timer = applicationContext.getBean(JwtTimeStore)
    }

    def cleanup() {
        applicationContext.close()
    }


    def 'should add and get token timers' () {
        given:
        def now = Instant.now().epochSecond

        expect:
        timer.getRange(0, now, 10) == []

        when:
        timer.add('foo', now-1)
        timer.add('bar', now-2)
        then:
        timer.getRange(0, now, 1) == ['bar']
        timer.getRange(0, now, 1) == ['foo']
        timer.getRange(0, now, 1) == []

        when:
        timer.add('foo', now+1)
        timer.add('bar', now+2)
        then:
        timer.getRange(0, now, 1) == []
        and:
        timer.getRange(0, now+5, 5) == ['foo','bar']
        and:
        timer.getRange(0, now+5, 5) == []
    }

}
