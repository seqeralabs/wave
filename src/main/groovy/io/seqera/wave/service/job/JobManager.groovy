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
import java.util.OptionalLong
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.graceful.GracefulShutdownCapable
import io.micronaut.scheduling.TaskExecutors
import io.seqera.data.workqueue.MessageConsumer
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

    /**
     * Number of {@link #processJob(JobSpec)} tasks submitted to the {@link #ioExecutor}
     * that have not completed yet
     */
    private final AtomicInteger processingInflight = new AtomicInteger()

    /**
     * Set when the application shutdown begins, see {@link #drain(Duration)}
     */
    private volatile boolean draining

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
        pendingQueue.addConsumer((job, lease)-> launchJob(job) ? Decision.ACK : Decision.RETRY)
        // job status checks run in parallel on the IO executor, each task settling its own lease
        processingQueue.addConsumer(new MessageConsumer<JobSpec>() {
            @Override
            Decision accept(JobSpec job, MessageLease lease) {
                return dispatchProcessJob(job, lease)
            }

            @Override
            boolean ready() {
                return canProcessJob()
            }
        })
    }

    /**
     * Admission gate of the processing queue: stop claiming jobs while `maxRunningJobs`
     * status checks are in flight
     */
    protected boolean canProcessJob() {
        // `maxRunningJobs` caps the running jobs across the whole cluster (see `launchJob0`), while
        // this counter is local to this replica: since every in-flight check holds a processing
        // queue entry, it can hardly reach the limit, so this gate is a safety net rather than a
        // per-replica cap on concurrent k8s status calls
        return processingInflight.get() < config.maxRunningJobs
    }

    /**
     * Submit the processing of a job to the {@link #ioExecutor}. The submitted task owns
     * the message lease from here on and settles it when {@link #processJob(JobSpec)} returns.
     *
     * @param job
     *      A {@link JobSpec} object representing the job to be processed
     * @param lease
     *      The {@link MessageLease} of the queue entry holding the job
     * @return
     *      {@link Decision#DEFERRED} when the task has been submitted, or {@link Decision#RETRY}
     *      when the executor rejected it, so the entry is redelivered once the lease expires
     */
    protected Decision dispatchProcessJob(JobSpec job, MessageLease lease) {
        processingInflight.incrementAndGet()
        try {
            final Future<?> task = ioExecutor.submit(() -> runProcessJob(job, lease))
            // the lease is renewed for as long as the task runs, so a slow status check
            // is never redelivered to another consumer while it is still in progress
            lease.bindLiveness(() -> !task.isDone())
            return Decision.DEFERRED
        }
        catch (RejectedExecutionException e) {
            processingInflight.decrementAndGet()
            log.warn "Unable to submit processing of job=${job.operationName} - cause: ${e.message}"
            return Decision.RETRY
        }
    }

    protected void runProcessJob(JobSpec job, MessageLease lease) {
        try {
            // do not start a status check once the shutdown has begun, it may not complete
            // within the drain timeout. The entry is redelivered to another replica
            if( draining )
                lease.retry()
            else if( processJob(job) )
                lease.ack()
            else
                lease.retry()
        }
        catch (Throwable e) {
            log.error "Unexpected error processing job=${job.operationName} - cause: ${e.message}", e
            lease.retry()
        }
        finally {
            processingInflight.decrementAndGet()
        }
    }

    /**
     * Drain the job queues when the application shutdown begins. Micronaut invokes this
     * before destroying any bean, so the jobs being processed can complete while the
     * services they depend on are still available.
     *
     * <p>Requires {@code micronaut.lifecycle.graceful-shutdown.enabled=true}. The drain
     * runs asynchronously because Micronaut invokes each {@link GracefulShutdownCapable}
     * bean sequentially on the same thread.
     */
    @Override
    CompletionStage<?> shutdownGracefully() {
        log.info "Draining job queues - timeout=${config.drainTimeout}"
        return CompletableFuture.runAsync(() -> drainQuietly())
    }

    @Override
    OptionalLong reportActiveTasks() {
        return OptionalLong.of(processingInflight.get())
    }

    private void drainQuietly() {
        try {
            if( !drain(config.drainTimeout) )
                log.warn "Job queues drain incomplete after ${config.drainTimeout} - jobs still processing=${processingInflight.get()}; they will be redelivered to another replica"
        }
        catch (Throwable e) {
            log.error "Unexpected error draining job queues - cause: ${e.message}", e
        }
    }

    /**
     * Drain the job queues within the given timeout:
     * <ol>
     *     <li>stop both queues claiming new jobs and wait for their dispatchers to complete
     *     the current cycle, including a job launch in progress. Bounded by a quarter of the
     *     timeout, so that the next step is always given the remaining time</li>
     *     <li>wait for the job status checks still running on the IO executor</li>
     *     <li>release the queues</li>
     * </ol>
     *
     * Jobs not completed when the timeout expires keep their queue entry and are
     * redelivered to another replica once their lease expires.
     *
     * @param timeout
     *      The maximum time to wait
     * @return
     *      {@code true} when the queues have been drained within the timeout, or {@code false} otherwise
     */
    protected boolean drain(Duration timeout) {
        draining = true
        final deadline = System.currentTimeMillis() + timeout.toMillis()
        // 1. stop claiming new jobs, quiescing both queues in parallel
        final quiesceTimeout = timeout.dividedBy(4)
        final pending = CompletableFuture.supplyAsync(() -> pendingQueue.awaitQuiescent(quiesceTimeout))
        final processingQuiesced = processingQueue.awaitQuiescent(quiesceTimeout)
        final pendingQuiesced = pending.join()
        final quiesced = processingQuiesced && pendingQuiesced
        // 2. wait for the status checks in flight
        while( processingInflight.get()>0 && System.currentTimeMillis()<deadline ) {
            try {
                Thread.sleep(100)
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                break
            }
        }
        final completed = processingInflight.get()==0
        // 3. release the queues with the remaining budget
        final remaining = Duration.ofMillis(Math.max(1, deadline - System.currentTimeMillis()))
        pendingQueue.close(remaining)
        processingQueue.close(remaining)
        return quiesced && completed
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
