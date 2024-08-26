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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
/**
 * Implement the logic to handle Blob cache transfer (uploads)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
@Requires(property = 'wave.blobCache.enabled', value = 'true')
class JobManager {

    @Inject
    private JobStrategy jobStrategy

    @Inject
    private JobQueue queue

    @Inject
    private JobDispatcher dispatcher

    @Inject
    private JobConfig config

    @PostConstruct
    void init() {
        log.info "Creating job manager - config=$config"
        queue.consume((job)-> processJob(job))
    }

    protected boolean processJob(JobId jobId) {
        try {
            return processJob0(jobId)
        }
        catch (Throwable err) {
            // in the case of an expected exception report the error condition by using `onJobException`
            dispatcher.onJobException(jobId, err)
            // finally return `true` to signal the job should not be processed anymore
            return true
        }
    }

    protected boolean processJob0(JobId jobId) {
        final duration = Duration.between(jobId.creationTime, Instant.now())
        final state = jobStrategy.status(jobId)
        log.trace "Job status id=${jobId.schedulerId}; state=${state}"
        final done =
                state.completed() ||
                // considered failed when remain in unknown status too long         
                (state.status==JobState.Status.UNKNOWN && duration>config.graceInterval)
        if( done ) {
            // publish the completion event
            dispatcher.onJobCompletion(jobId, state)
             // cleanup the job
            jobStrategy.cleanup(jobId, state.exitCode)
            return true
        }
        // set the await timeout nearly double as the blob transfer timeout, this because the
        // transfer pod can spend `timeout` time in pending status awaiting to be scheduled
        // and the same `timeout` time amount carrying out the transfer (upload) operation
        final max = (dispatcher.jobMaxDuration(jobId).toMillis() * 2.10) as long
        if( duration.toMillis()>max ) {
            dispatcher.onJobTimeout(jobId)
            return true
        }
        else {
            log.trace "== Job pending for completion $jobId"
            return false
        }
    }

}
