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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskScheduler
import io.seqera.wave.configuration.ContainerRequestConfig
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
        final expiration = Instant.now().plus(config.cache.duration)
        // put in the container store
        containerRequestStore.put(request.requestId, request, config.cache.duration)
        // when the workflowId is available schedule a refresh event
        if( request.type==ContainerRequest.Type.Container && request.identity.workflowId ) {
            final entry = new ContainerRequestRange.Entry(request.requestId, request.identity.workflowId, expiration)
            scheduleRefresh(entry)
        }
        // return the token data
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

    private static final String PREFIX = 'request/v1/'

    @Inject
    private TaskScheduler scheduler

    @Inject
    private ContainerRequestRange containerRequestRange

    @Inject
    private TowerClient towerClient

    protected void scheduleRefresh(Entry entry) {
        final future = Instant.now() + config.cache.checkInterval
        log.trace "Scheduling container request $entry - event ts=$future"
        containerRequestRange.add(entry, future)
    }

    @PostConstruct
    private void init() {
        log.info "Creating Container request watcher - ${config}"
        // use randomize initial delay to prevent multiple replicas running at the same time
        scheduler.scheduleAtFixedRate(
                config.watcher.delayRandomized,
                config.watcher.interval,
                this.&watch )
    }

    protected void watch() {
        final now = Instant.now()
        final keys = containerRequestRange.getEntriesUntil(now, config.watcher.count)
        for( Entry it : keys ) {
            try {
                check0(it, now)
            }
            catch (InterruptedException e) {
                Thread.interrupted()
            }
            catch (Throwable t) {
                log.error("Unexpected error in container request watcher while processing key: $it", t)
            }
        }
    }

    protected void check0(final Entry entry, final Instant now) {
        // 1. some sanity checks
        if( !entry.requestId ) {
            log.error "Missing refresh entry request id - offending entry=$entry"
            return
        }
        if( !entry.workflowId ) {
            log.error "Missing refresh entry workflow id - offending entry=$entry"
            return
        }
        if( !entry.expiration ) {
            log.error "Missing refresh entry expiration - offending entry=$entry"
            return
        }

        // 2. check if the request is near to expiration
        final deadline = entry.expiration - config.cache.checkInterval
        if(  now < deadline  ) {
            log.debug "Container request '${entry.requestId}' does not requires refresh - deadline=${deadline}; expiration=${entry.expiration}"
            scheduleRefresh(entry)
            return
        }

        // 3. check the request is still available
        final request = getRequest(entry.requestId)
        if( !request ) {
            log.error "Unable to find any container request for id '${entry.requestId}'"
            return
        }

        // 4. check the workflow is still running
        final workflow = describeWorkflow(request)
        if( !isWorkflowActive(workflow) ) {
            log.debug "Container request '${entry.requestId}' does not require refresh - workflow ${workflow.id} is not running"
            return
        }

        // 5. check the expiration is not beyond the max allowed
        final newExpire = entry.expiration + config.cache.checkInterval.multipliedBy(2)
        if(Duration.between(request.creationTime, newExpire) > config.cache.maxDuration) {
            log.info "Container request '${entry.requestId}' reached max allowed duration - expiration=${entry.expiration}; new expiration=${newExpire}; worklow=${workflow.id}"
            return
        }

        // 6. load the container persisted record
        final requestRecord = loadContainerRecord(entry.requestId)
        if( !requestRecord ) {
            log.error "Unable to find any container record for request '${entry.requestId}'"
            return
        }

        // 7. store the request with update expiration
        final newTtl = Duration.between(Instant.now(), newExpire)
        log.info "Container request '${entry.requestId}' expiration is extended by: ${newTtl}; at: ${newExpire}; (was: ${entry.expiration})"
        containerRequestStore.put(entry.requestId, request, newTtl)
        // update the expiration record
        requestRecord.expiration = newExpire
        persistenceService.saveContainerRequestAsync(requestRecord)
        // schedule a new refresh event
        scheduleRefresh(entry.withExpiration(newExpire))
    }

    protected Workflow describeWorkflow(ContainerRequest request) {
        final resp = towerClient.describeWorkflow(
                request.identity.towerEndpoint,
                JwtAuth.of(request.identity),
                request.identity.workspaceId,
                request.identity.workflowId)
        return resp?.workflow
    }

    protected boolean isWorkflowActive(Workflow workflow) {
        return workflow
                ? workflow.status==Workflow.WorkflowStatus.SUBMITTED || workflow.status==Workflow.WorkflowStatus.RUNNING
                : null
    }

}
