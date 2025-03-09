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
import java.util.concurrent.ExecutorService
import javax.annotation.PreDestroy

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
    private JobProcessingQueue processingQueue

    @Inject
    private JobPendingQueue pendingQueue

    @Inject
    private JobDispatcher dispatcher

    @Inject
    private JobManagerConfig config

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    // FIXME https://github.com/seqeralabs/wave/issues/747
    private AsyncCache<String,Instant> debounceCache

    private Thread scheduleJobThread

    @PostConstruct
    void init() {
        log.info "Creating job manager - config=$config"
        debounceCache = Caffeine
                .newBuilder()
                .expireAfterWrite(config.graceInterval.multipliedBy(2))
                .executor(ioExecutor)
                .buildAsync()
        processingQueue.addConsumer((job)-> processJob(job))
        // run the scheduler thread
        scheduleJobThread = Thread.ofVirtual().name("jobs-scheduler-thread").start(()->scheduleJobs())
    }

    protected void scheduleJobs() {
        try {
            scheduleJobs0()
        }
        catch (InterruptedException e) {
            log.debug "Got interrupted exception"
        }
        finally {
            log.debug "Exiting job scheduler thread"
        }

    }
    protected void scheduleJobs0() {
        int errors=0
        while( !Thread.currentThread().isInterrupted() ) {
            try {
                schedule0()
                errors=0
            }
            catch (InterruptedException e) {
                log.debug "Got interrupted exception"
                break
            }
            catch (Throwable e) {
                final delay = Math.min(Math.pow(2, errors++) * config.schedulerInterval.toMillis() as long, config.schedulerMaxDelay.toMillis())
                log.debug "Unexpected error while scheduling scheduling job [awaiting ${Duration.ofMillis(delay)}] - cause: ${e.message}", e
                Thread.sleep(delay)
            }
        }
    }

    protected void schedule0() {
        if( pendingQueue.length()==0 ) {
            log.trace "No pending jobs to schedule"
            // wait for new jobs
            sleep config.schedulerInterval.toMillis()
        }
        else {
            final processingQueueLen = processingQueue.length()
            final canLaunchNewJobs = processingQueueLen<config.maxRunningJobs
            final jobSpec = canLaunchNewJobs ? pendingQueue.poll() : null
            final submitted = jobSpec ? dispatcher.launchJob(jobSpec) : null
            if( log.isTraceEnabled() )
                log.trace "Processing queue len=${processingQueueLen}; job=${jobSpec}; submitted=${submitted}"
            if( submitted )
                processingQueue.offer(submitted)
            if( !canLaunchNewJobs )
                Thread.sleep(config.schedulerInterval.toMillis())
        }
    }

    /**
     * Process a job entry according the state modelled by the {@link JobSpec} object.
     *
     * @param jobSpec
     *      A {@link JobSpec} object representing the job to be processed
     * @return
     *      {@code true} to signal the process has been processed successfully and it should
     *      be removed from the underlying queue, or {@code false} if the job execution has
     *      not yet completed.
     */
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
        // FIXME https://github.com/seqeralabs/wave/issues/747
        return state0(job, config.graceInterval, debounceCache.synchronous())
    }

    protected JobState state0(final JobSpec job, final Duration graceInterval, final Cache<String,Instant> cache) {
        final key = job.operationName
        final state = jobService.status(job)
        // return directly non-unknown statuses
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
        final duration = Duration.between(jobSpec.launchTime, Instant.now())
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
            jobService.cleanup(jobSpec, null)
            return true
        }
        else {
            log.trace "== Job pending for completion ${jobSpec}"
            return false
        }
    }

    @PreDestroy
    void destroy() {
        scheduleJobThread.interrupt()
        try {
            scheduleJobThread.join(1_000)
        }
        catch (Exception e) {
            log.warn "Unable to join scheduler thread - cause: ${e.message}", e
        }
    }
}
