package io.seqera.wave.service.cache.impl

import spock.lang.Specification

import java.time.Duration

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(environments = ['test'])
class LocalCacheProviderTest extends Specification {

    @Inject
    LocalCacheProvider localCacheProvider


    def 'conditional put with current value when ke is not set'() {
        when: 'conditionally set a key that has no current value'
        def current = localCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofMillis(Long.MAX_VALUE))

        then: 'the provided value is returned'
        current == 'new-value'

        and: 'the value is set in the store'
        localCacheProvider.get('key') == 'new-value'
        
        when:
        def other = localCacheProvider.putIfAbsentAndGetCurrent('key', 'hola', Duration.ofMillis(Long.MAX_VALUE))
        then:
        // should not be set because it already exists
        other == 'new-value'
    }

    def 'conditional put with current value when key is already set'() {
        given: 'a store containing a mapping for key that is not expired'
        localCacheProvider.put('key','existing', Duration.ofMillis(Long.MAX_VALUE))

        when: 'try to conditionally set the key to a new value'
        def current = localCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofMillis(Long.MAX_VALUE))

        then: 'the existing value is returned'
        current == 'existing'

        and: 'the value is not updated in the store'
        localCacheProvider.get('key') == 'existing'
    }


    def 'conditional put with current value when key is set and has expired'() {
        given: 'a store containing a mapping for key that will expire'
        localCacheProvider.put('key', 'existing', Duration.ofMillis(100))
        // give time for cache store to expire the key
        sleep(Duration.ofMillis(200).toMillis())

        when: 'try to conditionally set the key to a new value'
        def current = localCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofMillis(100))

        then: 'the provided value is returned'
        current == 'new-value'

        and: 'the value is updated is set in the store'
        localCacheProvider.get('key') == 'new-value'
    }

    def 'should add and find keys for values' () {
        when:
        localCacheProvider.biPut('x1', 'a', Duration.ofMinutes(1))
        localCacheProvider.biPut('x2', 'b', Duration.ofMinutes(1))
        localCacheProvider.biPut('x3', 'a', Duration.ofMinutes(1))
        localCacheProvider.biPut('x4', 'c', Duration.ofMinutes(1))

        then:
        localCacheProvider.biKeysFor('a') == ['x1', 'x3'] as Set
        localCacheProvider.biKeysFor('c') == ['x4'] as Set
        localCacheProvider.biKeysFor('d') == [] as Set

        when:
        localCacheProvider.biRemove('x1')
        then:
        localCacheProvider.biKeysFor('a') == ['x3'] as Set

        when:
        localCacheProvider.biRemove('x3')
        then:
        localCacheProvider.biKeysFor('a') == [] as Set
    }

    def 'should add and find keys for values' () {
        when:
        localCacheProvider.biPut('x1', 'a', Duration.ofMillis(100))
        localCacheProvider.biPut('x2', 'b', Duration.ofMinutes(1))
        localCacheProvider.biPut('x3', 'a', Duration.ofMinutes(1))
        localCacheProvider.biPut('x4', 'c', Duration.ofMinutes(1))

        then:
        localCacheProvider.biKeyFind('a', false) == 'x1'
        and:
        localCacheProvider.biKeysFor('a') == ['x1','x3'] as Set
        and:
        sleep 500
        and:
        localCacheProvider.biKeyFind('a', false) == 'x3'
        localCacheProvider.biKeysFor('a') == ['x3'] as Set

    }
}
