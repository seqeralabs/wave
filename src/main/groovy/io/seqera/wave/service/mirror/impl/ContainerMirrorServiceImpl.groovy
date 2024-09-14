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

package io.seqera.wave.service.mirror.impl

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.service.job.JobEvent
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorStateStore
import io.seqera.wave.service.mirror.MirrorResult
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Named('Mirror')
@CompileStatic
class ContainerMirrorServiceImpl implements ContainerMirrorService, JobHandler {

    @Inject
    private MirrorStateStore store

    @Inject
    private JobService jobService

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService ioExecutor

    @Override
    MirrorResult mirror(MirrorRequest request) {
        if( store.putIfAbsent(request.targetImage, MirrorResult.from(request))) {
            // run mirror
            jobService.launchMirror(request)
        }

        return store.get(request.targetImage)
    }

    @Override
    CompletableFuture<MirrorResult> mirrorResult(String targetImage) {
        return CompletableFuture.supplyAsync(()-> store.awaitCompletion(targetImage), ioExecutor)
    }

    @Override
    void onJobEvent(JobEvent event) {
        final mirror = store.get(event.job.stateId)
        if( !mirror ) {
            log.error "== Mirror store entry unknown for job=${event.job.stateId}; event=${event}"
            return
        }
        if( mirror.done() ) {
            log.warn "== Mirror store entry already marked as completed for job=${event.job.stateId}; event=${event}"
            return
        }

        if( event.type == JobEvent.Type.Complete) {
            handleJobCompletion(event.job, mirror, event.state)
        }
        else if( event.type == JobEvent.Type.Timeout ) {
            handleJobTimeout(event.job, mirror)
        }
        else if( event.type == JobEvent.Type.Error ) {
            handleJobException(event.job, mirror, event.error)
        }
        else {
            throw new IllegalStateException("Unknown morror job event type=$event")
        }
    }

    void handleJobCompletion(JobSpec jobSpec, MirrorResult mirror, JobState jobState) {
        final result = mirror.complete(jobState.exitCode, jobState.stdout)
        store.put(mirror.targetImage, result)
        log.debug "Mirror container completed - job=${jobSpec.operationName}; result=${result}; state=${jobState}"
    }

    void handleJobTimeout(JobSpec jobSpec, MirrorResult mirror) {
        final result = mirror.complete(null, "Mirror container timed out")
        store.put(mirror.targetImage, result)
        log.warn "Mirror container timed out - job=${jobSpec.operationName}; result=${result}"
    }

    void handleJobException(JobSpec jobSpec, MirrorResult mirror, Throwable error) {
        final result = mirror.complete(null, error.message)
        store.put(mirror.targetImage, result)
        log.error("Mirror container errored - job=${jobSpec.operationName}; result=${result}", error)
    }
}
