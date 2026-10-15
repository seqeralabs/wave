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

package io.seqera.wave.service.request

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.core.annotation.Nullable
import io.micronaut.scheduling.TaskScheduler
import io.seqera.wave.configuration.ContainerRequestConfig
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.request.ContainerRequestRange.Entry
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.tower.client.Workflow
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
class ContainerRequestServiceImpl implements ContainerRequestService {

    @Inject
    private ContainerRequestStore containerRequestStore

    @Inject
    private ContainerRequestConfig config

    @Inject
    private PersistenceService persistenceService

    @Override
    TokenData computeToken(ContainerRequest request) {
        // Only plain container requests bound to a Platform workflow are lifecycle-tracked: builds/
        // mirrors are one-off, and without a workflowId there is nothing to poll Tower about.
        if( config.watcherEnabled && request.type==ContainerRequest.Type.Container && request.identity.workflowId ) {
            // Workflow-bound token: grant only a short access TTL in the store; the watcher renews it
            // while the run is active, so it lapses shortly after the run completes. The store TTL is
            // what governs access — but we advertise the hard lifetime ceiling (request time +
            // maxDuration) to the client, since the real grant rolls forward and would otherwise
            // surface as a tiny, ever-changing expiration.
            containerRequestStore.put(request.requestId, request, config.accessTtl)
            final ttlExpiration = Instant.now().plus(config.accessTtl)
            scheduleRefresh(new ContainerRequestRange.Entry(request.requestId, request.identity.workflowId, ttlExpiration))
            final maxExpiration = request.creationTime.plus(config.cacheMaxDuration)
            return new TokenData(request.requestId, maxExpiration)
        }
        // Everything else keeps the fixed default cache duration
        final expiration = Instant.now().plus(config.cacheDuration)
        containerRequestStore.put(request.requestId, request)
        return new TokenData(request.requestId, expiration)
    }

    @Override
    ContainerRequest getRequest(String requestId) {
        return containerRequestStore.get(requestId)
    }

    @Override
    ContainerRequest evictRequest(String requestId) {
        if(!requestId)
            return null

        final request = containerRequestStore.get(requestId)
        if( request ) {
            containerRequestStore.remove(requestId)
        }
        return request
    }

    @Override
    WaveContainerRecord loadContainerRecord(String requestId) {
        persistenceService.loadContainerRequest(requestId)
    }

    // =============== watcher implementation ===============

    @Inject
    private TaskScheduler scheduler

    @Inject
    private ContainerRequestRange containerRequestRange

    @Inject
    private TowerClient towerClient

    @Inject
    @Nullable
    private MeterRegistry meterRegistry

    /**
     * Runs the per-entry checks and the Platform lookups, so that a slow or unreachable Platform
     * endpoint only holds its own virtual thread instead of the whole watcher tick.
     */
    private final ExecutorService watcherExecutor = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * Platform endpoints whose lookup recently timed out, mapped to the instant until which they are
     * skipped. Their tokens are renewed without a lookup so they don't each pay the full timeout.
     */
    private final Map<String,Instant> unreachableEndpoints = new ConcurrentHashMap<>()

    /**
     * Workflow lookups currently in flight, so that concurrent checks of the many tokens of one run
     * share a single Platform call.
     */
    private final Map<String,Future<Workflow>> inflightLookups = new ConcurrentHashMap<>()

    /**
     * Prometheus counter for the token-refresh path. The {@code result} tag records each check
     * outcome:
     * <ul>
     *   <li>{@code renewed}    - workflow active, token renewed
     *   <li>{@code completed}  - workflow reached a confirmed terminal status, token left to lapse
     *   <li>{@code unresolved} - status could not be confirmed (unknown/null); token revoked fail-closed
     *   <li>{@code error}      - Platform lookup failed or timed out; token renewed fail-open
     *   <li>{@code backoff}    - Platform endpoint recently timed out; token renewed without a lookup
     *   <li>{@code expired}    - hard 48h lifetime cap reached
     *   <li>{@code gone}       - request already evicted from the store
     * </ul>
     * Describe-call volume is derivable as the sum of {@code renewed}, {@code completed},
     * {@code unresolved} and {@code error}. Registry may be absent in unit tests, hence the
     * null-safe navigation.
     */
    private void meterRefresh(String result) {
        meterRegistry?.counter('wave.tokens.refresh', 'result', result)?.increment()
    }

    private static final int MAX_CAUSE_DEPTH = 50

    /**
     * Whether the given error is an expected Platform connectivity failure (a timeout or an HTTP
     * error reaching Tower) as opposed to an unexpected bug. Such failures are transient, so they
     * should not be logged at ERROR with a full stack trace. The cause chain is walked up to
     * {@link #MAX_CAUSE_DEPTH} to guard against cyclic cause references.
     */
    protected static boolean isConnectivityError(Throwable t) {
        Throwable e = t
        for( int i = 0; e != null && i < MAX_CAUSE_DEPTH; e = e.cause, i++ ) {
            if( e instanceof TimeoutException || e instanceof HttpResponseException )
                return true
        }
        return false
    }

    /**
     * Whether the given error is a definite answer from Platform denying the lookup (e.g. 401, 403,
     * 404) rather than a transient failure. Request timeouts and rate limiting are transient.
     */
    protected static boolean isDefiniteRejection(Throwable t) {
        Throwable e = t
        for( int i = 0; e != null && i < MAX_CAUSE_DEPTH; e = e.cause, i++ ) {
            if( e instanceof HttpResponseException ) {
                final code = ((HttpResponseException) e).statusCode()?.code ?: 0
                return code >= 400 && code < 500 && code != 408 && code != 429
            }
        }
        return false
    }

    protected static String rootCauseMessage(Throwable t) {
        if( t == null )
            return null
        Throwable e = t
        int i = 0
        while( e.cause != null && e.cause != e && i++ < MAX_CAUSE_DEPTH )
            e = e.cause
        return e.message ?: e.class.simpleName
    }

    protected void scheduleRefresh(Entry entry) {
        // Re-check this request one refreshInterval from now. refreshInterval is deliberately shorter
        // than accessTtl so several checks fit inside a token's lifetime.
        final future = Instant.now() + config.refreshInterval
        log.trace "Scheduling container request $entry - event ts=$future"
        containerRequestRange.add(entry, future)
    }

    @PostConstruct
    private void init() {
        if( !config.watcherEnabled ) {
            log.info "Container request watcher is disabled - workflow-bound token lifecycle tracking off"
            return
        }
        log.info "Creating Container request watcher - access-ttl=${config.accessTtl}; refresh-interval=${config.refreshInterval}; max-duration=${config.cacheMaxDuration}; watcher-interval=${config.watcherInterval}; concurrency=${config.watcherConcurrency}; lookup-timeout=${config.watcherLookupTimeout}"
        // Randomize the initial delay so that, with multiple replicas, the watchers do not all
        // fire on the same tick and hammer Tower / the range store simultaneously.
        scheduler.scheduleAtFixedRate(
                config.watcherDelayRandomized,
                config.watcherInterval,
                this.&watch )
    }

    @PreDestroy
    private void destroy() {
        watcherExecutor.shutdownNow()
    }

    protected void watch() {
        final now = Instant.now()
        // getEntriesUntil atomically pops the due entries (score <= now), so across replicas each
        // entry is processed by exactly one watcher; the cap bounds the work done per tick.
        final keys = containerRequestRange.getEntriesUntil(now, config.watcherCount)
        if( !keys )
            return
        log.debug "Container request watcher processing ${keys.size()} entries"
        // Check the entries concurrently, bounded by the semaphore, so a slow Platform endpoint can
        // only delay its own tokens. The tick waits for all of them so ticks never overlap; each check
        // is bounded by the lookup timeout.
        final permits = new Semaphore(Math.max(1, config.watcherConcurrency))
        final futures = new ArrayList<Future>(keys.size())
        try {
            for( Entry it : keys ) {
                // copy the loop variable: a Groovy for-each variable is shared across iterations
                final entry = it
                permits.acquire()
                futures.add(watcherExecutor.submit({
                    try { process(entry, now) } finally { permits.release() }
                } as Runnable))
            }
            for( Future it : futures )
                it.get()
        }
        catch (InterruptedException | RejectedExecutionException e) {
            // Shutting down: the entries were already popped, so re-queue the ones never submitted
            // for another replica to pick up; the submitted ones re-queue themselves when interrupted.
            log.warn "Container request watcher interrupted - re-queueing ${keys.size()-futures.size()} unprocessed entries"
            futures.each { it.cancel(true) }
            for( int i=futures.size(); i<keys.size(); i++ )
                requeue(keys[i])
            if( e instanceof InterruptedException )
                Thread.currentThread().interrupt()
        }
    }

    private void requeue(Entry entry) {
        try {
            containerRequestRange.add(entry, Instant.now())
        }
        catch (Throwable t) {
            log.warn "Unable to re-queue container request entry $entry - cause: ${t.message}"
        }
    }

    protected void process(Entry entry, Instant now) {
        try {
            check0(entry, now)
        }
        catch (InterruptedException e) {
            log.warn "Container request watcher interrupted while processing key: $entry - re-queueing"
            requeue(entry)
            Thread.currentThread().interrupt()
        }
        catch (Throwable t) {
            // The entry was already popped by getEntriesUntil; re-add it so a transient failure
            // (e.g. a store blip) does not silently drop the request and let its token lapse.
            scheduleRefresh(entry)
            log.error("Unexpected error in container request watcher while processing key: $entry", t)
        }
    }

    protected void check0(final Entry entry, final Instant now) {
        // NOTE: this method either RENEWS the token (re-put + re-arm the next check) or STOPS
        // tracking it (return without re-arming) so the token lapses within one accessTtl. There is
        // no "not yet due" fast-path: every scheduled check that finds the workflow active renews.

        // 1. sanity checks — entries always carry a requestId/workflowId at creation, so a missing
        //    one means corrupt/stale serialized data in the range store; log and drop it.
        if( !entry.requestId ) {
            log.error "Missing refresh entry request id - offending entry=$entry"
            return
        }
        if( !entry.workflowId ) {
            log.error "Missing refresh entry workflow id - offending entry=$entry"
            return
        }

        // 2. The request may have been evicted from the store (token already lapsed); nothing left
        //    to renew — stop tracking.
        final request = getRequest(entry.requestId)
        if( !request ) {
            meterRefresh('gone')
            log.debug "Unable to find any container request for id '${entry.requestId}' - stop tracking"
            return
        }
        log.trace "Checking container request '${entry.requestId}' - workflow=${entry.workflowId}; current expiration=${entry.expiration}; now=${now}"

        // 3. Hard lifetime cap: never keep a token alive past requestCreationTime + maxDuration,
        //    even for a still-running workflow. Once reached, stop and let it lapse.
        final maxExpire = request.creationTime.plus(config.cacheMaxDuration)
        if( !now.isBefore(maxExpire) ) {
            meterRefresh('expired')
            log.info "Container request '${entry.requestId}' reached max allowed lifetime (${config.cacheMaxDuration}) - workflow=${entry.workflowId}; stop tracking"
            return
        }

        // 4. Skip the lookup for an endpoint that recently timed out, renewing fail-open instead.
        final endpoint = request.identity.towerEndpoint
        final backoffUntil = endpoint ? unreachableEndpoints.get(endpoint) : null
        if( backoffUntil && Instant.now().isBefore(backoffUntil) ) {
            meterRefresh('backoff')
            log.debug "Container request '${entry.requestId}' - endpoint ${endpoint} is backing off until ${backoffUntil}; renewing without lookup"
            renew(entry, request, now, maxExpire)
            return
        }

        // 5. Renew only while the workflow is still active. describeWorkflow is short-cached by
        //    workflowId so the many tokens of one run share a single Platform lookup. A failed or
        //    timed-out lookup renews the token anyway (fail-open): a Platform connectivity problem
        //    must not kill a running pipeline's image access. The max lifetime cap still applies.
        final Workflow workflow
        try {
            workflow = lookupWorkflow(request)
        }
        catch (InterruptedException e) {
            throw e
        }
        catch (Throwable t) {
            if( isDefiniteRejection(t) ) {
                // Platform answered: the workflow is gone or the credentials no longer grant access.
                // That is not a connectivity problem, so revoke fail-closed.
                meterRefresh('unresolved')
                log.info "Container request '${entry.requestId}' - workflow ${entry.workflowId} lookup rejected by Platform (${rootCauseMessage(t)}); stop tracking"
                return
            }
            meterRefresh('error')
            if( endpoint && t instanceof TimeoutException )
                unreachableEndpoints.put(endpoint, Instant.now().plus(config.watcherEndpointBackoff))
            if( isConnectivityError(t) )
                log.warn "Container request watcher could not confirm workflow status - request=${entry.requestId}; workflow=${entry.workflowId}; endpoint=${endpoint}; cause=${rootCauseMessage(t)}; renewing anyway"
            else
                log.error("Unexpected error looking up workflow status - request=${entry.requestId}; workflow=${entry.workflowId}; endpoint=${endpoint}; renewing anyway", t)
            renew(entry, request, now, maxExpire)
            return
        }
        if( endpoint )
            unreachableEndpoints.remove(endpoint)

        if( !isWorkflowActive(workflow) ) {
            // Distinguish a confirmed completion from a status we could not confirm (null/UNKNOWN):
            // the latter is a fail-closed revocation.
            final resolved = workflow!=null && workflow.status!=Workflow.WorkflowStatus.UNKNOWN
            meterRefresh(resolved ? 'completed' : 'unresolved')
            log.debug "Container request '${entry.requestId}' - workflow ${workflow?.id} ${resolved ? 'completed' : 'status unresolved'}; stop tracking"
            return
        }

        meterRefresh('renewed')
        renew(entry, request, now, maxExpire)
    }

    protected void renew(Entry entry, ContainerRequest request, Instant now, Instant maxExpire) {
        // Grant another accessTtl, but never beyond the max cap.
        final newExpire = [now.plus(config.accessTtl), maxExpire].min()
        final newTtl = Duration.between(now, newExpire)
        if( newTtl.isNegative() || newTtl.isZero() ) {
            // Defensive: a non-positive TTL must never reach the store, which could treat it as
            // "no expiry" and leave the token alive indefinitely.
            meterRefresh('expired')
            log.warn "Container request '${entry.requestId}' computed TTL is not positive: ${newTtl}; stop tracking"
            return
        }
        log.debug "Container request '${entry.requestId}' renewed for ${newTtl}; expires at ${newExpire}; workflow=${entry.workflowId}"
        containerRequestStore.put(entry.requestId, request, newTtl)
        // mirror the new expiration onto the persisted record for display (in-place JSON update, not
        // a re-insert — the record already exists from request creation)
        persistenceService.updateContainerExpirationAsync(entry.requestId, newExpire)
        // re-arm the next check
        scheduleRefresh(entry.withExpiration(newExpire))
    }

    protected Workflow lookupWorkflow(ContainerRequest request) {
        // Bound the Platform lookup so one hanging endpoint cannot hold a check for minutes.
        // Concurrent checks of the same workflow share one in-flight lookup, so the many tokens of a
        // run cost a single Platform call. Cancelling interrupts the virtual thread blocked on the
        // Tower client.
        final identity = request.identity
        final key = "${identity.towerEndpoint}|${identity.workspaceId}|${identity.workflowId}|${JwtAuth.of(identity).key}".toString()
        final Future<Workflow> future = inflightLookups.computeIfAbsent(key, (String k) -> {
            final FutureTask<Workflow>[] self = new FutureTask[1]
            self[0] = new FutureTask<Workflow>({
                try { return describeWorkflow(request) }
                finally { inflightLookups.remove(k, self[0]) }
            } as Callable<Workflow>)
            watcherExecutor.execute(self[0])
            return (Future<Workflow>) self[0]
        })
        try {
            return future.get(config.watcherLookupTimeout.toMillis(), TimeUnit.MILLISECONDS)
        }
        catch (TimeoutException | CancellationException e) {
            // a timed-out lookup is cancelled for every waiter
            future.cancel(true)
            inflightLookups.remove(key, future)
            throw new TimeoutException("Workflow lookup timed out after ${config.watcherLookupTimeout}")
        }
        catch (InterruptedException e) {
            future.cancel(true)
            throw e
        }
        catch (ExecutionException e) {
            throw e.cause ?: e
        }
    }

    protected Workflow describeWorkflow(ContainerRequest request) {
        // Reuse the request's own Platform identity/credentials to ask Tower about the workflow.
        // The client short-caches the response by workflowId so all tokens of one run coalesce onto
        // a single lookup; the small staleness is bounded well below accessTtl.
        final resp = towerClient.describeWorkflow(
                request.identity.towerEndpoint,
                JwtAuth.of(request.identity),
                request.identity.workspaceId,
                request.identity.workflowId)
        final workflow = resp?.workflow
        log.debug "Fetched workflow status for container request '${request.requestId}' - workflow=${request.identity.workflowId}; endpoint=${request.identity.towerEndpoint}; status=${workflow?.status}"
        return workflow
    }

    protected boolean isWorkflowActive(Workflow workflow) {
        // Active == not yet finished. A null workflow (unknown / not returned) counts as inactive
        // so we err on the side of letting the token expire rather than extending forever.
        final status = workflow?.status
        return status==Workflow.WorkflowStatus.SUBMITTED || status==Workflow.WorkflowStatus.RUNNING
    }

}
