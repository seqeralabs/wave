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
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.graceful.GracefulShutdownCapable
import io.micronaut.scheduling.TaskExecutors
import io.seqera.data.workqueue.MessageConsumer.Decision
import io.seqera.data.workqueue.MessageLease
import io.seqera.util.trace.TraceElapsedTime
import io.seqera.wave.configuration.JobManagerConfig
import io.seqera.wave.configuration.WaveLite
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
@Requires(missingBeans = WaveLite)
@CompileStatic
class JobManager implements GracefulShutdownCapable {

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

    private Cache<String,Instant> debounceCache

    @PostConstruct
    void init() {
        log.info "Creating job manager - config=$config"
        debounceCache = Caffeine
                .newBuilder()
                .expireAfterWrite(config.graceInterval.multipliedBy(2))
                .executor(ioExecutor)
                .build()
        // a `false` outcome settles the message as RETRY, leaving it queued for
        // redelivery once the lease expires; `true` acknowledges and removes it
        pendingQueue.addConsumer((job, lease)-> {
            keepAlive(lease)
            return launchJob(job) ? Decision.ACK : Decision.RETRY
        })
        processingQueue.addConsumer((job, lease)-> {
            keepAlive(lease)
            return processJob(job) ? Decision.ACK : Decision.RETRY
        })
    }

    /**
     * Keep the lease renewed for as long as the consumer runs, so a slow job launch or status
     * check is never redelivered to another replica while it is still in progress. The probe
     * can be constant because the consumer is synchronous: the lease is registered before the
     * consumer is invoked and released when it returns
     */
    protected static void keepAlive(MessageLease lease) {
        lease.bindLiveness(() -> true)
    }

    /**
     * Drain the job queues when the application shutdown begins. Micronaut invokes this
     * before destroying any bean, so the jobs being launched or checked can complete while
     * the services they depend on are still available. Requires
     * {@code micronaut.lifecycle.graceful-shutdown.enabled=true}.
     *
     * <p>Jobs not completed within the drain timeout keep their queue entry and are
     * redelivered to another replica once their lease expires.
     */
    @Override
    CompletionStage<?> shutdownGracefully() {
        log.info "Draining job queues - timeout=${config.drainTimeout}"
        // close both queues in parallel, Micronaut invokes each shutdown hook on the same thread
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> pendingQueue.close(config.drainTimeout)),
                CompletableFuture.runAsync(() -> processingQueue.close(config.drainTimeout)) )
    }

    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:500}')
    protected boolean launchJob0(JobSpec job) {
        // the processing queue is shared by all replicas and holds one entry per running job, so
        // `maxRunningJobs` is a cluster-wide limit, not a per-replica one. It is a soft limit: the
        // length is read before launching, so replicas launching concurrently can overshoot it
        final processingQueueLen = processingQueue.length()
        final canLaunchNewJobs = processingQueueLen < config.maxRunningJobs
        if( canLaunchNewJobs ) {
            final submitted = dispatcher.launchJob(job)
            if( log.isTraceEnabled() )
                log.trace "Processing queue len=${processingQueueLen}; job=${job}; submitted=${submitted}"
            if( submitted )
                processingQueue.offer(submitted)
        }
        else {
            log.debug "Processing queue is full (${processingQueueLen}) - delaying submission of job=$job"
        }
        return canLaunchNewJobs
    }

    /**
     * Launch a job execution adding it to the {@link JobProcessingQueue} queue
     *
     * @param jobSpec
     *      A {@link JobSpec} object representing the job to be launched
     * @return
     *      {@code true} to signal the process has been launched successfully and it should
     *      be removed from the underlying pending queue, or {@code false} if the job cannot be
     *      launched because the processing queue is full.
     */
    protected boolean launchJob(JobSpec jobSpec) {
        try {
            return launchJob0(jobSpec)
        }
        catch (Throwable err) {
            // in the case of an expected exception report the error condition by using `onJobException`
            dispatcher.notifyJobException(jobSpec, err)
            // note: return `true` to mark the entry as consumed so that the job is not processed anymore
            return true
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
            // note: return `true` to mark the entry as consumed so that the job is not processed anymore
            return true
        }
    }

    protected JobState state(JobSpec job) {
        return state0(job, config.graceInterval, debounceCache)
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

    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:500}')
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

}
