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

package io.seqera.wave.service.builder

import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService

import io.micronaut.scheduling.TaskExecutors
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.service.state.impl.LocalCacheProvider
import jakarta.inject.Inject
import jakarta.inject.Named

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class BuildCacheStoreLocalTest extends Specification {

    @Inject
    private BuildConfig buildConfig

    @Inject
    @Named(TaskExecutors.IO)
    ExecutorService ioExecutor

    BuildResult zeroResult = BuildResult.create('0')
    BuildResult oneResult = BuildResult.completed('1', 0, 'done', Instant.now(), 'abc')
    BuildResult twoResult = BuildResult.completed('2', 0, 'done', Instant.now(), 'abc')
    BuildResult threeResult = BuildResult.completed('3', 0, 'done', Instant.now(), 'abc')

    def zeroRequest = new BuildRequest(
            targetImage: 'docker.io/foo:0',
            buildId: '0',
            startTime: Instant.now(),
            maxDuration: Duration.ofMinutes(1)
    )
    def oneRequest = new BuildRequest(
            targetImage: 'docker.io/foo:1',
            buildId: '1',
            startTime: Instant.now(),
            maxDuration: Duration.ofMinutes(1)
    )
    def twoRequest = new BuildRequest(
            targetImage: 'docker.io/foo:2',
            buildId: '2',
            startTime: Instant.now(),
            maxDuration: Duration.ofMinutes(1)
    )
    def threeRequest = new BuildRequest(
            targetImage: 'docker.io/foo:3',
            buildId: '3',
            startTime: Instant.now(),
            maxDuration: Duration.ofMinutes(1)
    )

    def zero = new BuildStoreEntry(zeroRequest, zeroResult)
    def one = new BuildStoreEntry(oneRequest, oneResult)
    def two = new BuildStoreEntry(twoRequest, twoResult)
    def three = new BuildStoreEntry(threeRequest, threeResult)

    def 'should get and put key values' () {
        given:
        def provider = new LocalCacheProvider()
        def cache = new BuildCacheStore(provider, buildConfig, ioExecutor)

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
        def cache = Spy(new BuildCacheStore(provider, buildConfig, ioExecutor)) { getDuration() >> DURATION }

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
        def cache = new BuildCacheStore(provider, buildConfig, ioExecutor)

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
        def cache = new BuildCacheStore(provider, buildConfig, ioExecutor)

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
        def cache = new BuildCacheStore(provider, buildConfig, ioExecutor)

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
        result.get() == one.result

        when:
        cache.storeBuild('foo',two)
        cache.storeBuild('foo',three)
        then:
        cache.awaitBuild('foo').get() == three.result
    }

}
