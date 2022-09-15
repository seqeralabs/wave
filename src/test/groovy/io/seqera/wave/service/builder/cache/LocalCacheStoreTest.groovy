package io.seqera.wave.service.builder.cache

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalCacheStoreTest extends Specification {

    BuildRequest zero = new BuildRequest("0", Path.of('work'),"","",null, ContainerPlatform.DEFAULT,null,null)
    BuildRequest one = new BuildRequest("1", Path.of('work'),"","",null,ContainerPlatform.DEFAULT,null,null)
    BuildRequest two = new BuildRequest("2", Path.of('work'),"","",null,ContainerPlatform.DEFAULT,null,null)
    BuildRequest three = new BuildRequest("3", Path.of('work'),"","",null,ContainerPlatform.DEFAULT,null,null)

    def 'should get and put key values' () {
        given:
        def cache = new LocalCacheStore()
        
        expect:
        cache.get('foo') == null
        and:
        !cache.containsKey('foo')

        when:
        cache.put('foo', one)
        then:
        cache.get('foo') == one
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
        cache.put('foo',zero)
        // update a value in a separate thread
        Thread.start { sleep 500; cache.put('foo',one) }
        // stops until the value is updated
        def result = cache.await('foo')
        then:
        result == one

        when:
        cache.put('foo',two)
        cache.put('foo',three)
        then:
        cache.await('foo')==three

    }

}
