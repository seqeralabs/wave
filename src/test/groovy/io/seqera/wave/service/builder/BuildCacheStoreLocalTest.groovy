/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service.builder

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.cache.impl.LocalCacheProvider
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildCacheStoreLocalTest extends Specification {

    BuildResult zero = BuildResult.create('0')
    BuildResult one = BuildResult.completed('1', 0, 'done', Instant.now())
    BuildResult two = BuildResult.completed('2', 0, 'done', Instant.now())
    BuildResult three = BuildResult.completed('3', 0, 'done', Instant.now())

    def 'should get and put key values' () {
        given:
        def provider = new LocalCacheProvider()
        def cache = new BuildCacheStore(provider, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(60))

        expect:
        cache.getBuild('foo') == null

        when:
        cache.storeBuild('foo', one)
        then:
        cache.getBuild('foo') == one
    }

    def 'should retain value for max duration' () {
        given:
        def DURATION = Duration.ofSeconds(2)
        def provider = new LocalCacheProvider()
        def cache = new BuildCacheStore(provider, Duration.ofSeconds(5), Duration.ofSeconds(30), DURATION)

        expect:
        cache.getBuild('foo') == null

        when:
        cache.storeBuild('foo', one)
        then:
        cache.getBuild('foo') == one
        and:
        sleep DURATION.toMillis().intdiv(2)
        cache.getBuild('foo') == one
        and:
        sleep DURATION.toMillis()
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

    def 'should store if absent' () {
        given:
        def provider = new LocalCacheProvider()
        def cache = new BuildCacheStore(provider, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(60))

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
        def provider = new LocalCacheProvider()
        def cache = new BuildCacheStore(provider, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(60))

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
        def provider = new LocalCacheProvider()
        def cache = new BuildCacheStore(provider, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(60))

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
