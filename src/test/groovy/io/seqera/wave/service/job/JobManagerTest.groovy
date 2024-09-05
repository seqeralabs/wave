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
import java.time.Instant
import java.util.concurrent.ExecutorService


/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class JobManagerTest extends Specification {
    def 'processJob should handle valid TransferJobSpec'() {
        given:
        def jobService = Mock(JobService)
        def jobDispatcher = Mock(JobDispatcher)
        def ioExecutor = Mock(ExecutorService)
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher, ioExecutor: ioExecutor)
        and:
        def jobSpec = new JobSpec(JobSpec.Type.Transfer, 'foo', Instant.now(), Duration.ofMinutes(10), 'scheduler-1')

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
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher)
        and:
        def jobSpec = new JobSpec(JobSpec.Type.Transfer, 'foo', Instant.now(), Duration.ofMinutes(10), 'scheduler-1')

        when:
        def result = manager.processJob(jobSpec)

        then:
        1 * jobService.status(jobSpec) >> { throw new RuntimeException('Error') }
        1 * jobDispatcher.notifyJobError(jobSpec, _)
        result
    }

    def 'processJob0 should timeout TransferJobSpec when duration exceeds max limit'() {
        given:
        def jobService = Mock(JobService)
        def jobDispatcher = Mock(JobDispatcher)
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher)
        and:
        def jobSpec = new JobSpec(JobSpec.Type.Transfer, 'foo', Instant.now() - Duration.ofMinutes(5), Duration.ofMinutes(2), 'scheduler-1')

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
        def manager = new JobManager(jobService: jobService, dispatcher: jobDispatcher)
        and:
        def jobSpec = new JobSpec(JobSpec.Type.Transfer, 'foo', Instant.now().minus(Duration.ofMillis(500)), Duration.ofMinutes(10), 'scheduler-1')

        when:
        def result = manager.processJob0(jobSpec)

        then:
        1 * jobService.status(jobSpec) >> JobState.running()
        !result
    }
}
