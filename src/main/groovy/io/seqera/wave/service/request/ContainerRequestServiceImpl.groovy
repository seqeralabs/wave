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
     * Prometheus counter for the token-refresh path. The {@code result} tag records each check
     * outcome so operators can watch the fail-closed revocations Gavin flagged:
     * <ul>
     *   <li>{@code renewed}    - workflow active, token renewed
     *   <li>{@code completed}  - workflow reached a confirmed terminal status, token left to lapse
     *   <li>{@code unresolved} - status could not be confirmed (unknown/null); token revoked fail-closed
     *   <li>{@code error}      - Platform lookup threw (unreachable); check retried with slack
     *   <li>{@code expired}    - hard 48h lifetime cap reached
     *   <li>{@code gone}       - request already evicted from the store
     * </ul>
     * Describe-call volume is derivable as the sum of all results except {@code gone} (which stops
     * before the lookup). Registry may be absent in unit tests, hence the null-safe navigation.
     */
    private void meterRefresh(String result) {
        meterRegistry?.counter('wave.tokens.refresh', 'result', result)?.increment()
    }

    // Cause chains are never deep; cap the walk so a cyclic chain (e.g. A->B->A) cannot spin forever.
    private static final int MAX_CAUSE_DEPTH = 50

    /**
     * Whether the given error is an expected Platform connectivity failure (a timeout or an HTTP
     * error reaching Tower) as opposed to an unexpected bug. Such failures are transient and retried,
     * so they should not be logged at ERROR with a full stack trace. The cause chain is walked up to
     * {@link #MAX_CAUSE_DEPTH} to guard against cyclic cause references.
     */
    protected static boolean isConnectivityError(Throwable t) {
        Throwable e = t
        for( int i = 0; e != null && i < MAX_CAUSE_DEPTH; e = e.cause, i++ ) {
            if( e instanceof TimeoutException || e instanceof HttpResponseException )
                return true
            if( e == e.cause )
                break
        }
        return false
    }

    protected static String rootCauseMessage(Throwable t) {
        Throwable e = t
        for( int i = 0; e.cause != null && e.cause != e && i < MAX_CAUSE_DEPTH; e = e.cause, i++ ) { }
        return e.message ?: e.class.simpleName
    }

    protected void scheduleRefresh(Entry entry) {
        // Re-check this request one refreshInterval from now. refreshInterval is deliberately shorter
        // than accessTtl so several checks fit inside a token's lifetime — a transient Tower failure
        // can be retried before the token would lapse.
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
        log.info "Creating Container request watcher - access-ttl=${config.accessTtl}; refresh-interval=${config.refreshInterval}; max-duration=${config.cacheMaxDuration}; watcher-interval=${config.watcherInterval}"
        // Randomize the initial delay so that, with multiple replicas, the watchers do not all
        // fire on the same tick and hammer Tower / the range store simultaneously.
        scheduler.scheduleAtFixedRate(
                config.watcherDelayRandomized,
                config.watcherInterval,
                this.&watch )
    }

    protected void watch() {
        final now = Instant.now()
        // getEntriesUntil atomically pops the due entries (score <= now), so across replicas each
        // entry is processed by exactly one watcher; the cap bounds the work done per tick.
        final keys = containerRequestRange.getEntriesUntil(now, config.watcherCount)
        if( keys )
            log.debug "Container request watcher processing ${keys.size()} entries"
        for( Entry it : keys ) {
            try {
                check0(it, now)
            }
            catch (InterruptedException e) {
                // Preserve the interrupt flag so a shutdown request is not swallowed by this loop.
                log.warn "Container request watcher interrupted while processing key: $it"
                Thread.currentThread().interrupt()
            }
            catch (Throwable t) {
                meterRefresh('error')
                // The entry was already popped by getEntriesUntil; re-add it so a transient failure
                // (e.g. a Tower blip) does not silently drop the request and let its token lapse.
                scheduleRefresh(it)
                if( isConnectivityError(t) ) {
                    // Expected when the workflow's Platform endpoint is briefly unreachable — e.g. a
                    // paired self-hosted instance whose pairing WebSocket times out. The token is not
                    // renewed, so it lapses fail-closed; log a concise warning rather than a full
                    // stack trace to avoid flooding the logs while the entry is retried.
                    log.warn "Container request watcher could not confirm workflow status - request=${it.requestId}; workflow=${it.workflowId}; cause=${rootCauseMessage(t)}; will retry"
                }
                else {
                    log.error("Unexpected error in container request watcher while processing key: $it", t)
                }
            }
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

        // 3. Renew only while the workflow is still active. describeWorkflow is short-cached by
        //    workflowId so the many tokens of one run share a single Platform lookup; completion is
        //    still observed well within accessTtl. isWorkflowActive treats an unknown/failed lookup
        //    as inactive (fail-closed).
        final workflow = describeWorkflow(request)
        if( !isWorkflowActive(workflow) ) {
            // Distinguish a confirmed completion from a status we could not confirm (null/UNKNOWN):
            // the latter is a fail-closed revocation — the signal to watch for connectivity issues.
            final resolved = workflow!=null && workflow.status!=Workflow.WorkflowStatus.UNKNOWN
            meterRefresh(resolved ? 'completed' : 'unresolved')
            log.debug "Container request '${entry.requestId}' - workflow ${workflow?.id} ${resolved ? 'completed' : 'status unresolved'}; stop tracking"
            return
        }

        // 4. Hard lifetime cap: never keep a token alive past requestCreationTime + maxDuration,
        //    even for a still-running workflow. Once reached, stop and let it lapse.
        final maxExpire = request.creationTime.plus(config.cacheMaxDuration)
        if( !now.isBefore(maxExpire) ) {
            meterRefresh('expired')
            log.info "Container request '${entry.requestId}' reached max allowed lifetime (${config.cacheMaxDuration}) - workflow=${workflow.id}; stop tracking"
            return
        }

        // 5. Renew: grant another accessTtl, but never beyond the max cap.
        final newExpire = [now.plus(config.accessTtl), maxExpire].min()
        final newTtl = Duration.between(now, newExpire)
        if( newTtl.isNegative() || newTtl.isZero() ) {
            // Defensive: a non-positive TTL must never reach the store, which could treat it as
            // "no expiry" and leave the token alive indefinitely.
            meterRefresh('expired')
            log.warn "Container request '${entry.requestId}' computed TTL is not positive: ${newTtl}; stop tracking"
            return
        }
        meterRefresh('renewed')
        log.debug "Container request '${entry.requestId}' renewed for ${newTtl}; expires at ${newExpire}; workflow=${workflow.id}"
        containerRequestStore.put(entry.requestId, request, newTtl)
        // mirror the new expiration onto the persisted record for display (in-place JSON update, not
        // a re-insert — the record already exists from request creation)
        persistenceService.updateContainerExpirationAsync(entry.requestId, newExpire)
        // re-arm the next check
        scheduleRefresh(entry.withExpiration(newExpire))
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
