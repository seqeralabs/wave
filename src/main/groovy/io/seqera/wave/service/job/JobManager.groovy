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
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.util.ExponentialAttempt
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
@Requires(property = 'wave.blobCache.enabled', value = 'true')
class JobManager {

    @Inject
    private JobStrategy jobStrategy

    @Inject
    private JobQueue queue

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Inject
    private JobDispatcher dispatcher

    @Inject
    private JobConfig config

    private final ExponentialAttempt attempt = new ExponentialAttempt()

    @PostConstruct
    private init() {
        CompletableFuture.supplyAsync(()->run(), executor)
    }

    void run() {
        log.info "+ Starting Job manager"
        while( !Thread.currentThread().isInterrupted() ) {
            try {
                final jobId = queue.poll(config.pollTimeout)

                if( jobId ) {
                    handle(jobId)
                    attempt.reset()
                }
            }
            catch (InterruptedException e) {
                log.debug "Interrupting transfer manager watcher thread"
                break
            }
            catch (Throwable e) {
                final d0 = attempt.delay()
                log.error("Transfer manager unexpected error (await: ${d0}) - cause: ${e.message}", e)
                sleep(d0.toMillis())
            }
        }
    }

    /**
     * Handles the blob transfer operation i.e. check and update the current upload status
     *
     * @param blobId the blob cache id i.e. {@link BlobCacheInfo#id()}
     */
    protected void handle(JobId jobId) {
        try {
            try {
                handle0(jobId)
            }
            catch (Throwable error) {
                dispatcher.onJobException(jobId, error)
            }
        }
        catch (InterruptedException e) {
            // re-queue the transfer to not lose it
            queue.offer(jobId)
            // re-throw the exception
            throw e
        }
    }

    protected void handle0(JobId jobId) {
        final duration = Duration.between(jobId.creationTime, Instant.now())
        final state = jobStrategy.status(jobId)
        log.trace "Job status id=${jobId.schedulerId}; state=${state}"
        final done =
                state.completed() ||
                // considered failed when remain in unknown status too long         
                (state.status==JobState.Status.UNKNOWN && duration>config.graveInterval)
        if( done ) {
            // publish the completion event
            dispatcher.onJobCompletion(jobId, state)
             // cleanup the job
            jobStrategy.cleanup(jobId, state.exitCode)
            return
        }
        // set the await timeout nearly double as the blob transfer timeout, this because the
        // transfer pod can spend `timeout` time in pending status awaiting to be scheduled
        // and the same `timeout` time amount carrying out the transfer (upload) operation
        final max = (dispatcher.jobRunTimeout(jobId).toMillis() * 2.10) as long
        if( duration.toMillis()>max ) {
            dispatcher.onJobTimeout(jobId)
        }
        else {
            log.trace "== Job pending for completion $jobId"
            // re-schedule for a new check
            queue.offer(jobId)
        }
    }

}
