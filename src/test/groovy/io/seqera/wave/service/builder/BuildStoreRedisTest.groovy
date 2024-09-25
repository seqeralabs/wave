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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.seqera.wave.service.job.JobFactory
import io.seqera.wave.service.job.JobQueue
import io.seqera.wave.test.RedisTestContainer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildStoreRedisTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        flushRedis()
    }

    def cleanup(){
        applicationContext.close()
    }

    def flushRedis() {
        // The use of Redis flush removes also data structures like consumer groups
        // causing unexpected exceptions in other components. Only remove entries
        // creates by this tests
        def cacheStore = applicationContext.getBean(BuildStore)
        cacheStore.removeBuild('foo')
    }

    def 'should get and put key values' () {
        given:
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def entry = new BuildState(req, res)
        and:
        def cacheStore = applicationContext.getBean(BuildStore)
        
        expect:
        cacheStore.getBuild('foo') == null

        when:
        cacheStore.storeBuild('foo', entry)
        then:
        cacheStore.getBuild('foo') == entry

    }

    def 'should expire entry' () {
        given:
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def entry = new BuildState(req, res)
        and:
        def cacheStore = applicationContext.getBean(BuildStore)

        expect:
        cacheStore.getBuild('foo') == null

        when:
        cacheStore.storeBuild('foo', entry)
        then:
        cacheStore.getBuild('foo') == entry
        and:
        sleep 1000
        cacheStore.getBuild('foo') == entry

        when:
        cacheStore.storeBuild('foo', entry, Duration.ofSeconds(1))
        then:
        cacheStore.getBuild('foo') == entry
        and:
        sleep 500
        cacheStore.getBuild('foo') == entry
        and:
        sleep 1_500
        cacheStore.getBuild('foo') == null
    }

    def 'should store if absent' () {
        given:
        def res1 = BuildResult.create('1')
        def req1 = new BuildRequest(
                targetImage: 'docker.io/foo:1',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def entry1 = new BuildState(req1, res1)
        def res2 = BuildResult.create('2')
        def req2 = new BuildRequest(
                targetImage: 'docker.io/foo:2',
                buildId: '2',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def entry2 = new BuildState(req2, res2)
        and:
        def store = applicationContext.getBean(BuildStore)

        expect:
        // the value is store because the key does not exists
        store.storeIfAbsent('foo', entry1)
        and:
        // the value is return
        store.getBuild('foo') == entry1
        and:
        // storing a new value fails because the key already exist
        !store.storeIfAbsent('foo', entry2)
        and:
        // the previous value is returned
        store.getBuild('foo') == entry1

    }

    def 'should remove a build entry' () {
        given:
        def res = BuildResult.create('0')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '0',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def entry = new BuildState(req, res)
        def cache = applicationContext.getBean(BuildStore)

        when:
        cache.storeBuild('foo', entry)
        then:
        cache.getBuild('foo') == entry

        when:
        cache.removeBuild('foo')
        then:
        cache.getBuild('foo') == null

    }

    @Timeout(value=10)
    def 'should await for a value' () {
        given:
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:1',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofSeconds(10)
        )
        def entry = new BuildState(req, res)
        and:
        def cacheStore = applicationContext.getBean(BuildStore) as BuildStore

        when:
        // insert a value
        cacheStore.storeBuild('foo', entry)

        // update a value in a separate thread
        Thread.start {
            res = BuildResult.completed('1', 0, '', Instant.now(), null)
            entry = entry.withResult(res)
            cacheStore.storeBuild('foo', entry)
        }

        // wait the value is updated
        sleep 1500
        def result = cacheStore.awaitBuild('foo')

        then:
        result.get() == entry.result
    }

    @Timeout(value=30)
    def 'should abort an await if build never finish' () {
        given:
        def buildCacheStore = applicationContext.getBean(BuildStoreImpl)
        def jobQueue = applicationContext.getBean(JobQueue)
        def jobFactory = applicationContext.getBean(JobFactory)
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:1',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofSeconds(5)
        )
        def entry = new BuildState(req, res)
        and:
        buildCacheStore.storeIfAbsent(req.targetImage, entry)
        jobQueue.offer(jobFactory.build(req))

        when: "wait for an update never will arrive"
        buildCacheStore.awaitBuild(req.targetImage).get()

        then: "job will timeout and the build will be marked as failed"
        buildCacheStore.getBuild(req.targetImage).result.exitStatus == -1
    }

}
