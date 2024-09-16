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

package io.seqera.wave.service.job

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class JobManagerTest extends Specification {

    def 'processJob should handle valid TransferJobSpec'() {
        given:
        def jobService = Mock(JobService)
        def jobDispatcher = Mock(JobDispatcher)
        def config = new JobConfig(graceInterval: Duration.ofMillis(1))
        def cache = Caffeine.newBuilder().build()
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher, config: config, unknownCache: cache)
        and:
        def jobSpec = JobSpec.transfer('foo', 'scheduler-1', Instant.now(), Duration.ofMinutes(10))

        when:
        def result = manager.processJob0(jobSpec)

        then:
        1 * jobService.status(jobSpec) >> JobState.completed(0, 'My job logs')
        1 * jobDispatcher.notifyJobCompletion(jobSpec, _)
        result
    }

    def 'processJob should handle exception for TransferJobSpec'() {
        given:
        def jobService = Mock(JobService)
        def jobDispatcher = Mock(JobDispatcher)
        def config = new JobConfig(graceInterval: Duration.ofMillis(1))
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher, config: config)
        and:
        def jobSpec = JobSpec.transfer('foo', 'scheduler-1', Instant.now(), Duration.ofMinutes(10))

        when:
        def result = manager.processJob(jobSpec)

        then:
        1 * jobService.status(jobSpec) >> { throw new RuntimeException('Error') }
        1 * jobDispatcher.notifyJobException(jobSpec, _)
        result
    }

    def 'processJob0 should timeout TransferJobSpec when duration exceeds max limit'() {
        given:
        def jobService = Mock(JobService)
        def jobDispatcher = Mock(JobDispatcher)
        def config = new JobConfig(graceInterval: Duration.ofMillis(1))
        def cache = Caffeine.newBuilder().build()
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher, config:config, unknownCache: cache)
        and:
        def jobSpec = JobSpec.transfer('foo', 'scheduler-1', Instant.now() - Duration.ofMinutes(5), Duration.ofMinutes(2))

        when:
        def result = manager.processJob0(jobSpec)

        then:
        1 * jobService.status(jobSpec) >> JobState.running()
        1 * jobDispatcher.notifyJobTimeout(jobSpec)
        result
    }

    def 'processJob0 should requeue TransferJobSpec when duration is within limits'() {
        given:
        def jobService = Mock(JobService)
        def jobDispatcher = Mock(JobDispatcher)
        def config = new JobConfig(graceInterval: Duration.ofMillis(1))
        def cache = Caffeine.newBuilder().build()
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher, config: config, unknownCache: cache)
        and:
        def jobSpec = JobSpec.transfer('foo', 'scheduler-1', Instant.now().minus(Duration.ofMillis(500)), Duration.ofMinutes(10))

        when:
        def result = manager.processJob0(jobSpec)

        then:
        1 * jobService.status(jobSpec) >> JobState.running()
        !result
    }

    def 'should validate unknown state cache' () {
        given:
        Cache<String,Instant> cache = Caffeine
                .newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build()
        and:
        def jobService = Mock(JobService)
        def manager = new JobManager(jobService: jobService)
        and:
        def job1 = new JobSpec('1', JobSpec.Type.Build, '1', '1', Instant.now(), Duration.ofMinutes(1), Mock(Path))
        and:
        def PENDING = new JobState(JobState.Status.PENDING, null, null)
        def UNKNOWN = new JobState(JobState.Status.UNKNOWN, null, null)
        def FAILED = new JobState(JobState.Status.FAILED, null, null)
        def _100_ms = Duration.ofMillis(100)
        def _1_sec = Duration.ofSeconds(1)
        and:
        JobState result

        when:
        result = manager.state0(job1, _1_sec, cache)
        then:
        jobService.status(job1) >> PENDING
        and:
        result == PENDING
        cache.getIfPresent('1') == null

        // now return an unknown status
        when:
        result = manager.state0(job1, _100_ms, cache)
        then:
        jobService.status(job1) >> UNKNOWN
        and:
        result == UNKNOWN
        cache.getIfPresent('1') != null

        // the following state is pending, so unknown is cleared
        when:
        sleep 150
        result = manager.state0(job1, _100_ms, cache)
        then:
        jobService.status(job1) >> PENDING
        and:
        result == PENDING
        cache.getIfPresent('1') == null

        // now two unknown for longer than the grace period
        when:
        result = manager.state0(job1, _100_ms, cache)
        then:
        jobService.status(job1) >> UNKNOWN
        and:
        result == UNKNOWN
        cache.getIfPresent('1') != null

        when:
        sleep 150
        and:
        result = manager.state0(job1, _100_ms, cache)
        then:
        jobService.status(job1) >> UNKNOWN
        and:
        result == FAILED
        cache.getIfPresent('1') == null

    }
}
