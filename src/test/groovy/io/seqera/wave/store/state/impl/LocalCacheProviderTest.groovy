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

package io.seqera.wave.store.state.impl

import spock.lang.Specification

import java.time.Duration

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(environments = ['test'])
class LocalCacheProviderTest extends Specification {

    @Inject
    LocalCacheProvider localCacheProvider


    def 'should get and put a key-value pair' () {
        given:
        def k = UUID.randomUUID().toString()

        expect:
        localCacheProvider.get(k) == null

        when:
        localCacheProvider.put(k, "hello")
        then:
        localCacheProvider.get(k) == 'hello'
    }

    def 'should get and put a key-value pair with ttl' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        expect:
        localCacheProvider.get(k) == null

        when:
        localCacheProvider.put(k, "hello", Duration.ofMillis(TTL))
        then:
        localCacheProvider.get(k) == 'hello'
        then:
        sleep(TTL *2)
        and:
        localCacheProvider.get(k) == null
    }

    def 'should get and put only if absent' () {
        given:
        def k = UUID.randomUUID().toString()

        expect:
        localCacheProvider.get(k) == null

        when:
        def done = localCacheProvider.putIfAbsent(k, 'foo')
        then:
        done 
        and:
        localCacheProvider.get(k) == 'foo'

        when:
        done = localCacheProvider.putIfAbsent(k, 'bar')
        then:
        !done
        and:
        localCacheProvider.get(k) == 'foo'
    }

    def 'should get and put if absent with ttl' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        when:
        def done = localCacheProvider.putIfAbsent(k, 'foo', Duration.ofMillis(TTL))
        then:
        done
        and:
        localCacheProvider.get(k) == 'foo'

        when:
        done = localCacheProvider.putIfAbsent(k, 'bar', Duration.ofMillis(TTL))
        then:
        !done
        and:
        localCacheProvider.get(k) == 'foo'

        when:
        sleep(TTL *2)
        and:
        done = localCacheProvider.putIfAbsent(k, 'bar', Duration.ofMillis(TTL))
        then:
        done
        and:
        localCacheProvider.get(k) == 'bar'
    }

    def 'should put and remove a value' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        when:
        localCacheProvider.put(k, 'foo')
        then:
        localCacheProvider.get(k) == 'foo'

        when:
        localCacheProvider.remove(k)
        then:
        localCacheProvider.get(k) == null
    }

}
