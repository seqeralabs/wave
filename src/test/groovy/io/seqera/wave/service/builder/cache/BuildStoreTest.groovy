package io.seqera.wave.service.builder.cache

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStoreCache
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(rebuildContext = true)
class BuildStoreTest extends Specification {

    BuildResult zero = BuildResult.create('0')
    BuildResult one = BuildResult.completed('1', 0, 'done', Instant.now())
    BuildResult two = BuildResult.completed('2', 0, 'done', Instant.now())
    BuildResult three = BuildResult.completed('3', 0, 'done', Instant.now())

    @Inject
    BuildStoreCache cache

    @Property(name="specIndex",value="1")
    @Property(name="wave.build.status.duration",value="60s")
    @Property(name="wave.build.status.delay",value="5s")
    @Property(name="wave.build.status.timeout",value="30s")
    def 'should get and put key values' () {
        expect:
        cache.getBuild('foo') == null

        when:
        cache.storeBuild('foo', one)
        then:
        cache.getBuild('foo') == one
    }

    @Property(name="specIndex",value="2")
    @Property(name="wave.build.status.duration",value="2s")
    @Property(name="wave.build.status.delay",value="5s")
    @Property(name="wave.build.status.timeout",value="30s")
    def 'should retain value for max duration' () {
        expect:
        cache.getBuild('foo') == null

        when:
        cache.storeBuild('foo', one)
        then:
        cache.getBuild('foo') == one
        and:
        sleep Duration.ofSeconds(2).toMillis().intdiv(2)
        cache.getBuild('foo') == one
        and:
        sleep Duration.ofSeconds(2).toMillis()
        cache.getBuild('foo') == null

        when:
        cache.storeBuild('foo', one, Duration.ofSeconds(1))
        then:
        sleep 500
        cache.getBuild('foo') == one
        and:
        sleep 1_000
        cache.getBuild('foo') == null
    }

    @Property(name="specIndex",value="3")
    @Property(name="wave.build.status.duration",value="60s")
    @Property(name="wave.build.status.delay",value="5s")
    @Property(name="wave.build.status.timeout",value="30s")
    def 'should store if absent' () {
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

    @Property(name="specIndex",value="4")
    @Property(name="wave.build.status.duration",value="60s")
    @Property(name="wave.build.status.delay",value="5s")
    @Property(name="wave.build.status.timeout",value="30s")
    def 'should remove a build entry' () {
        when:
        cache.storeBuild('foo', zero)
        then:
        cache.getBuild('foo') == zero

        when:
        cache.removeBuild('foo')
        then:
        cache.getBuild('foo') == null

    }

    @Property(name="specIndex",value="5")
    @Property(name="wave.build.status.duration",value="60s")
    @Property(name="wave.build.status.delay",value="5s")
    @Property(name="wave.build.status.timeout",value="30s")
    def 'should await for a value' () {
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
