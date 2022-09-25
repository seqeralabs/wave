package io.seqera.wave.service.builder.cache

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.impl.LocalCacheStore
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalBuildStoreTest extends Specification {

    BuildResult zero = BuildResult.create('0')
    BuildResult one = BuildResult.completed('1', 0, 'done', Instant.now())
    BuildResult two = BuildResult.completed('2', 0, 'done', Instant.now())
    BuildResult three = BuildResult.completed('3', 0, 'done', Instant.now())

    def 'should get and put key values' () {
        given:
        def cache = new LocalCacheStore(delay: Duration.ofSeconds(5), timeout: Duration.ofSeconds(30))
        
        expect:
        cache.getBuild('foo') == null

        when:
        cache.storeBuild('foo', one)
        then:
        cache.getBuild('foo') == one
    }

    def 'should store if absent' () {
        given:
        def cache = new LocalCacheStore(delay: Duration.ofSeconds(5), timeout: Duration.ofSeconds(30))

        expect:
        cache.storeIfAbsent('foo', zero)
        and:
        cache.getBuild('foo') == zero
        and:
        // store of a new value return false
        !cache.storeIfAbsent('foo', one)
        and:
        // the previous value is still avail
        cache.getBuild('foo') == zero

    }

    def 'should remove a build entry' () {
        given:
        def cache = new LocalCacheStore(delay: Duration.ofSeconds(5), timeout: Duration.ofSeconds(30))

        when:
        cache.storeBuild('foo', zero)
        then:
        cache.getBuild('foo') == zero

        when:
        cache.removeBuild('foo')
        then:
        cache.getBuild('foo') == null

    }

    def 'should await for a value' () {
        given:
        def cache = new LocalCacheStore(delay: Duration.ofSeconds(5), timeout: Duration.ofSeconds(30))

        expect:
        cache.awaitBuild('foo') == null

        when:
        // insert a value
        cache.storeBuild('foo', zero)
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
