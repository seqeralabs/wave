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

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class JwtKeyGeneratorTest extends Specification {

    @Singleton
    static class Bean1 {

        int count

        Integer foo(JwtAuth auth) {
            return ++count
        }

    }

    @Singleton
    static class Bean2 {

        int count

        @Cacheable(cacheNames = 'cache-test', keyGenerator = JwtKeyGenerator)
        Integer foo(JwtAuth auth) {
            return ++count
        }

    }


    def 'should use class name to create cache key' () {
        given:
        ApplicationContext ctx = ApplicationContext.run('test')
        def bean1 = ctx.getBean(Bean1)
        def bean2 = ctx.getBean(Bean2)

        expect:
        bean1.foo(new JwtAuth('1', '2', '3', '4')) == 1
        bean1.foo(new JwtAuth('1', '2', '3', '4')) == 2
        bean1.foo(new JwtAuth('1', '2', '3', '4')) == 3

        and: '`key`, `createdAt` and `updatedAt` fields are ignored'
        bean2.foo(new JwtAuth('1', '2', '3', '4', Instant.now().plusSeconds(1))) == 1
        bean2.foo(new JwtAuth('1', '2', '3', '4', Instant.now().plusSeconds(2))) == 1
        bean2.foo(new JwtAuth('1', '2', '3', '4', Instant.now().plusSeconds(3))) == 1
        bean2.foo(new JwtAuth('X', '2', '3', '4', Instant.now().plusSeconds(1))) == 1

        and: 'changing endpoint, bearer or refresh, then the method is invoked'
        bean2.foo(new JwtAuth('1', 'X', '3', '4', Instant.now().plusSeconds(2))) == 2
        bean2.foo(new JwtAuth('1', '2', 'X', '4', Instant.now().plusSeconds(3))) == 3
        bean2.foo(new JwtAuth('1', '2', '3', 'X', Instant.now().plusSeconds(3))) == 4

        cleanup:
        ctx.stop()


    }

}
