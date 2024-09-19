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

package io.seqera.wave.service.cache.impl

import spock.lang.Specification

import java.time.Duration

import groovy.transform.Canonical
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.StateRecord
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class AbstractCacheStoreTest extends Specification {

    @Inject LocalCacheProvider provider

    static public long ttlMillis = 100

    @Canonical
    static class MyObject {
        String field1
        String field2
    }

    static class MyState extends MyObject implements StateRecord {

        MyState(String field1, String field2) {
            super(field1, field2)
        }

        @Override
        String getRecordId() {
            return field1
        }
    }

    static class MyCacheStore extends AbstractCacheStore<MyObject> {

        MyCacheStore(CacheProvider<String, String> provider) {
            super(provider, new MoshiEncodeStrategy<MyObject>() {})
        }

        @Override
        protected String getPrefix() {
            return 'test/v1:'
        }

        @Override
        protected Duration getDuration() {
            return Duration.ofMillis(ttlMillis)
        }
    }

    def 'should get key' () {
        given:
        def store = new MyCacheStore(provider)
        
        expect:
        store.key0('one') == 'test/v1:one'
    }

    def 'should get record id' () {
        given:
        def store = new MyCacheStore(provider)

        expect:
        store.recordId0('one') == 'test/v1:state/one'
    }

    def 'should get and put a value' () {
        given:
        def store = new MyCacheStore(provider)
        def key = UUID.randomUUID().toString()

        expect:
        store.get(key) == null

        when:
        store.put(key, new MyObject('this','that'))
        then:
        store.get(key) == new MyObject('this','that')
    }

    def 'should get and put a value' () {
        given:
        def store = new MyCacheStore(provider)
        def key = UUID.randomUUID().toString()

        expect:
        store.get(key) == null

        when:
        store.put(key, new MyObject('this','that'))
        then:
        store.get(key) == new MyObject('this','that')
        
        when:
        sleep ttlMillis *2
        then:
        store.get(key) == null
    }

    def 'should get and put a value with custom ttl' () {
        given:
        def store = new MyCacheStore(provider)
        def key = UUID.randomUUID().toString()

        expect:
        store.get(key) == null

        when:
        store.put(key, new MyObject('this','that'), Duration.ofSeconds(10))
        then:
        store.get(key) == new MyObject('this','that')

        when:
        sleep ttlMillis *2
        then:
        store.get(key) == new MyObject('this','that')
    }

    def 'should put and remove and item' () {
        given:
        def store = new MyCacheStore(provider)
        def key = UUID.randomUUID().toString()

        when:
        store.put(key, new MyObject('this','that'), Duration.ofSeconds(10))
        then:
        store.get(key) == new MyObject('this','that')

        when:
        store.remove(key)
        then:
        store.get(key) == null
    }

    def 'should put if absent' () {
        given:
        def store = new MyCacheStore(provider)
        def key = UUID.randomUUID().toString()

        when:
        def done = store.putIfAbsent(key, new MyObject('this','that'))
        then:
        done
        and:
        store.get(key) == new MyObject('this','that')

        when:
        done = store.putIfAbsent(key, new MyObject('xx','yy'))
        then:
        !done
        and:
        store.get(key) == new MyObject('this','that')

        when:
        sleep ttlMillis*2
        done = store.putIfAbsent(key, new MyObject('xx','yy'))
        then:
        done
        and:
        store.get(key) == new MyObject('xx','yy')

    }

    def 'should put if absent with custom ttl' () {
        given:
        def store = new MyCacheStore(provider)
        def key = UUID.randomUUID().toString()

        when:
        def done = store.putIfAbsent(key, new MyObject('this','that'), Duration.ofSeconds(10))
        then:
        done
        and:
        store.get(key) == new MyObject('this','that')

        when:
        done = store.putIfAbsent(key, new MyObject('xx','yy'))
        then:
        !done
        and:
        store.get(key) == new MyObject('this','that')

        when:
        sleep ttlMillis*2
        done = store.putIfAbsent(key, new MyObject('xx','yy'))
        then:
        !done
        and:
        store.get(key) == new MyObject('this','that')

    }


    def 'should put and get value by record id' () {
        given:
        def store = new MyCacheStore(provider)
        def recId = UUID.randomUUID().toString()
        def key =  UUID.randomUUID().toString()

        expect:
        store.get(key) == null
        store.getByRecordId(recId) == null

        when:
        def value = new MyState(recId, 'value')
        store.put(key, value)
        then:
        store.get(key) == value
        store.getByRecordId(recId) == value
        and:
        store.get(recId) == null
        store.getByRecordId(key) == null
    }


    def 'should put and get value by record id if absent' () {
        given:
        def store = new MyCacheStore(provider)
        def recId = UUID.randomUUID().toString()
        def key =  UUID.randomUUID().toString()

        expect:
        store.get(key) == null
        store.getByRecordId(recId) == null

        when:
        def value = new MyState(recId, 'value')
        def done = store.putIfAbsent(key, value)
        then:
        done
        and:
        store.get(key) == value
        store.getByRecordId(recId) == value
        and:
        store.get(recId) == null
        store.getByRecordId(key) == null

        when:
        done = store.putIfAbsent(key, new MyState('xx', 'yy'))
        then:
        !done
        and:
        store.get(key) == value
        store.getByRecordId(recId) == value
    }

}
