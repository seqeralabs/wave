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

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskExecutors
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named

/**
 * Implement the logic to handle Blob cache transfer (uploads)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
class JobManager {

    @Inject
    private JobService jobService

    @Inject
    private JobQueue queue

    @Inject
    private JobDispatcher dispatcher

    @Inject
    private JobConfig config

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService ioExecutor

    @PostConstruct
    void init() {
        log.info "Creating job manager - config=$config"
        queue.addConsumer((job)-> processJob(job))
    }

    protected boolean processJob(JobSpec jobSpec) {
        try {
            return processJob0(jobSpec)
        }
        catch (Throwable err) {
            // in the case of an expected exception report the error condition by using `onJobException`
            dispatcher.notifyJobError(jobSpec, err)
            // note: return `true` to signal the job should not be processed anymore
            return true
        }
    }

    protected boolean processJob0(JobSpec jobSpec) {
        final duration = Duration.between(jobSpec.creationTime, Instant.now())
        final state = jobService.status(jobSpec)
        log.trace "Job status id=${jobSpec.operationName}; state=${state}"
        final done =
                state.completed() ||
                // considered failed when remain in unknown status too long         
                (state.status==JobState.Status.UNKNOWN && duration>config.graceInterval)
        if( done ) {
            // publish the completion event
            dispatcher.notifyJobCompletion(jobSpec, state)
             // cleanup the job
            CompletableFuture.runAsync(()-> jobService.cleanup(jobSpec, state.exitCode), ioExecutor)
            return true
        }
        // set the await timeout nearly double as the job timeout, this because the
        // job can spend `timeout` time in pending status awaiting to be scheduled
        // and the same `timeout` time amount carrying out job operation
        final max = (jobSpec.maxDuration.toMillis() * 2.10) as long
        if( duration.toMillis()>max ) {
            dispatcher.notifyJobTimeout(jobSpec)
            return true
        }
        else {
            log.trace "== Job pending for completion ${jobSpec}"
            return false
        }
    }

}
