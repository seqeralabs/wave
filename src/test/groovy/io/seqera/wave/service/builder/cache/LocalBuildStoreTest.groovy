package io.seqera.wave.service.builder.cache

import spock.lang.Specification

import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.impl.LocalCacheStore
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalBuildStoreTest extends Specification {

    BuildResult zero = BuildResult.create('0')
    BuildResult one = BuildResult.create('1')
    BuildResult two = BuildResult.create('2')
    BuildResult three = BuildResult.create('3')

    def 'should get and put key values' () {
        given:
        def cache = new LocalCacheStore()
        
        expect:
        cache.getBuild('foo') == null
        and:
        !cache.hasBuild('foo')

        when:
        cache.storeBuild('foo', one)
        then:
        cache.getBuild('foo') == one
        and:
        cache.hasBuild('foo')
    }

    def 'should await for a value' () {
        given:
        def cache = new LocalCacheStore()

        expect:
        cache.awaitBuild('foo') == null

        when:
        // insert a value
        cache.storeBuild('foo',zero)
        // update a value in a separate thread
        Thread.start { sleep 500; cache.storeBuild('foo',one) }
        // stops until the value is updated
        def result = cache.awaitBuild('foo')
        then:
        result.get() == one

        when:
        cache.storeBuild('foo',two)
        cache.storeBuild('foo',three)
        then:
        cache.awaitBuild('foo').get()==three

    }

}
