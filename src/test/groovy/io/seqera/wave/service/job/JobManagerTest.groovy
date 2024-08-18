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

import java.time.Duration
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class JobManagerTest extends Specification {

    def "handle should process valid transferId"() {
        given:
        def queue = Mock(JobQueue)
        def jobStrategy = Mock(JobStrategy)
        def jobDispatcher = Mock(JobDispatcher)
        def manager = new JobManager(queue: queue, jobStrategy: jobStrategy, dispatcher: jobDispatcher)
        and:
        def job = JobId.transfer('foo')

        when:
        manager.handle(job)
        then:
        1 * jobStrategy.status(job) >> JobState.completed(0, 'My job logs')
        and:
        1 * jobDispatcher.onJobCompletion(job, _) >> { JobId id, JobState state ->
            assert state.exitCode == 0
            assert state.stdout == 'My job logs'
        }
        and:
        1 * jobStrategy.cleanup(job,0)
        and:
        0 * queue.offer(_)
    }

    def "handle should log error for unknown transferId"() {
        given:
        def queue = Mock(JobQueue)
        def jobStrategy = Mock(JobStrategy)
        def jobDispatcher = Mock(JobDispatcher)
        def manager = new JobManager(queue: queue, jobStrategy: jobStrategy, dispatcher: jobDispatcher)
        and:
        def job = JobId.transfer('unknown')

        when:
        manager.handle(job)
        then:
        1 * jobStrategy.status(job) >> null
        and:
        1 * jobDispatcher.onJobException(job,_)
        and:
        0 * queue.offer(_)
    }

    def "handle0 should fail transfer when status is unknown and duration exceeds grace period"() {
        given:
        def queue = Mock(JobQueue)
        def jobStrategy = Mock(JobStrategy)
        def jobDispatcher = Mock(JobDispatcher)
        def config = new JobConfig(graveInterval: Duration.ofMillis(500))
        def manager = new JobManager(queue: queue, jobStrategy: jobStrategy, dispatcher: jobDispatcher, config:config)
        and:
        def job = JobId.transfer('foo')

        when:
        sleep 1_000 //sleep longer than grace period
        manager.handle(job)

        then:
        1 * jobStrategy.status(job) >> JobState.unknown('logs')
        1 * jobDispatcher.onJobCompletion(job, _)
        1 * jobStrategy.cleanup(job, _)
        and:
        0 * queue.offer(_)
    }

    def "should requeue transfer when duration is within limits"() {
        given:
        def queue = Mock(JobQueue)
        def jobStrategy = Mock(JobStrategy)
        def jobDispatcher = Mock(JobDispatcher)
        def manager = new JobManager(queue: queue, jobStrategy: jobStrategy, dispatcher: jobDispatcher)
        and:
        def job = JobId.transfer('foo')

        when:
        manager.handle(job)
        then:
        1 * jobStrategy.status(job) >> JobState.running()
        1 * jobDispatcher.jobRunTimeout(job) >> Duration.ofSeconds(10)
        and:
        1 * queue.offer(job)
    }

    def "handle0 should timeout transfer when duration exceeds max limit"() {
        given:
        def queue = Mock(JobQueue)
        def jobStrategy = Mock(JobStrategy)
        def jobDispatcher = Mock(JobDispatcher)
        def manager = new JobManager(queue: queue, jobStrategy: jobStrategy, dispatcher: jobDispatcher)
        and:
        def job = JobId.transfer('foo')

        when:
        sleep 1_000 //await timeout
        manager.handle(job)
        then:
        1 * jobStrategy.status(job) >> JobState.running()
        1 * jobDispatcher.jobRunTimeout(job) >> Duration.ofMillis(100)
        and:
        1 * jobDispatcher.onJobTimeout(job)
        and:
        0 * queue.offer(_)
    }
}
