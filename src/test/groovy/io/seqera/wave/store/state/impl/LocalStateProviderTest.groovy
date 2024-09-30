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
class LocalStateProviderTest extends Specification {

    @Inject
    LocalStateProvider provider


    def 'should get and put a key-value pair' () {
        given:
        def k = UUID.randomUUID().toString()

        expect:
        provider.get(k) == null

        when:
        provider.put(k, "hello")
        then:
        provider.get(k) == 'hello'
    }

    def 'should get and put a key-value pair with ttl' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        expect:
        provider.get(k) == null

        when:
        provider.put(k, "hello", Duration.ofMillis(TTL))
        then:
        provider.get(k) == 'hello'
        then:
        sleep(TTL *2)
        and:
        provider.get(k) == null
    }

    def 'should get and put only if absent' () {
        given:
        def k = UUID.randomUUID().toString()

        expect:
        provider.get(k) == null

        when:
        def done = provider.putIfAbsent(k, 'foo')
        then:
        done 
        and:
        provider.get(k) == 'foo'

        when:
        done = provider.putIfAbsent(k, 'bar')
        then:
        !done
        and:
        provider.get(k) == 'foo'
    }

    def 'should get and put if absent with ttl' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        when:
        def done = provider.putIfAbsent(k, 'foo', Duration.ofMillis(TTL))
        then:
        done
        and:
        provider.get(k) == 'foo'

        when:
        done = provider.putIfAbsent(k, 'bar', Duration.ofMillis(TTL))
        then:
        !done
        and:
        provider.get(k) == 'foo'

        when:
        sleep(TTL *2)
        and:
        done = provider.putIfAbsent(k, 'bar', Duration.ofMillis(TTL))
        then:
        done
        and:
        provider.get(k) == 'bar'
    }

    def 'should get and put if absent and increment' () {
        given:
        def ttlMillis = 100
        def k = UUID.randomUUID().toString()
        def c = UUID.randomUUID().toString()

        expect:
        provider.get(k) == null

        when:
        def result = provider.putIfAbsent(k, 'foo', Duration.ofMillis(ttlMillis), c)
        then:
        result.v1
        result.v2 == 'foo'
        result.v3 == 1
        and:
        provider.get(k) == 'foo'

        when:
        result = provider.putIfAbsent(k, 'bar', Duration.ofMillis(ttlMillis), c)
        then:
        !result.v1
        result.v2 == 'foo'
        result.v3 == 1
        and:
        provider.get(k) == 'foo'

        when:
        sleep(ttlMillis *2)
        and:
        result = provider.putIfAbsent(k, 'foo', Duration.ofMillis(ttlMillis), c)
        then:
        result.v1
        result.v2 == 'foo'
        result.v3 == 2
    }

    def 'should put and remove a value' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        when:
        provider.put(k, 'foo')
        then:
        provider.get(k) == 'foo'

        when:
        provider.remove(k)
        then:
        provider.get(k) == null
    }

}
