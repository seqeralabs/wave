package io.seqera.wave.service.builder.cache

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalCacheStoreTest extends Specification {

    def 'should get and put key values' () {
        given:
        def cache = new LocalCacheStore()
        
        expect:
        cache.get('foo') == null
        and:
        !cache.containsKey('foo')

        when:
        cache.put('foo', 1)
        then:
        cache.get('foo') == 1
        and:
        cache.containsKey('foo')
    }

    def 'should await for a value' () {
        given:
        def cache = new LocalCacheStore()

        expect:
        cache.await('foo') == null

        when:
        // insert a value
        cache.put('foo',0)
        // update a value in a separate thread
        Thread.start { sleep 500; cache.put('foo',1) }
        // stops until the value is updated
        def result = cache.await('foo')
        then:
        result == 1

        when:
        cache.put('foo',2)
        cache.put('foo',3)
        then:
        cache.await('foo')==3

    }

}
