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

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
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
class JobManager {

    @Inject
    private JobService jobService

    @Inject
    private JobQueue queue

    @Inject
    private JobDispatcher dispatcher

    @Inject
    private JobConfig config

    private Cache<String,Instant> unknownCache

    @PostConstruct
    void init() {
        log.info "Creating job manager - config=$config"
        unknownCache = Caffeine.newBuilder().expireAfterWrite(config.graceInterval.multipliedBy(2)).build()
        queue.addConsumer((job)-> processJob(job))
    }


    protected boolean processJob(JobSpec jobSpec) {
        try {
            return processJob0(jobSpec)
        }
        catch (Throwable err) {
            // in the case of an expected exception report the error condition by using `onJobException`
            dispatcher.notifyJobException(jobSpec, err)
            // note: return `true` to signal the job should not be processed anymore
            return true
        }
    }

    protected JobState state(JobSpec job) {
        return state0(job, config.graceInterval, unknownCache)
    }

    protected JobState state0(final JobSpec job, final Duration graceInterval, final Cache<String,Instant> cache) {
        final key = job.operationName
        final state = jobService.status(job)
        // non unknown error return directly
        if( state.status != JobState.Status.UNKNOWN ) {
            cache.invalidate(key)
            return state
        }
        // check how long it returns an unknown persistently
        final initial = cache.get(key, (String)-> Instant.now())
        final delta = Duration.between(initial, Instant.now())
        // if it's less than the grace period return it
        if( delta <= graceInterval ) {
            return state
        }
        // if longer then the grace period, return a FAILED status to force an error
        cache.invalidate(key)
        return new JobState(JobState.Status.FAILED, null, state.stdout)
    }

    protected boolean processJob0(JobSpec jobSpec) {
        final duration = Duration.between(jobSpec.creationTime, Instant.now())
        final state = state(jobSpec)
        log.trace "Job status id=${jobSpec.operationName}; state=${state}"
        if( state.completed() ) {
            // publish the completion event
            dispatcher.notifyJobCompletion(jobSpec, state)
             // cleanup the job
            jobService.cleanup(jobSpec, state.exitCode)
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
