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

package io.seqera.wave.service.mirror

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.service.builder.BuildTrack
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.scan.ContainerScanService
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 * Implement a service to mirror a container image to a repository specified by the user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Named('Mirror')
@CompileStatic
class ContainerMirrorServiceImpl implements ContainerMirrorService, JobHandler<MirrorEntry> {

    @Inject
    private MirrorStateStore store

    @Inject
    private JobService jobService

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService ioExecutor

    @Inject
    private PersistenceService persistence

    @Inject
    @Nullable
    private ContainerScanService scanService

    @Inject
    private MetricsService metricsService

    /**
     * {@inheritDoc}
     */
    @Override
    BuildTrack mirrorImage(MirrorRequest request) {
        if( store.putIfAbsent(request.targetImage, MirrorEntry.of(request))) {
            log.info "== Container mirror submitted - request=$request"
            //increment mirror counter
            CompletableFuture.supplyAsync(() -> metricsService.incrementMirrorsCounter(request.identity), ioExecutor)
            jobService.launchMirror(request)
            return new BuildTrack(request.mirrorId, request.targetImage, false, null)
        }
        final ret = store.get(request.targetImage)
        if( ret ) {
            log.info "== Container mirror hit cache - request=$request"
            // note: mark as cached only if the build result is 'done'
            // if the build is still in progress it should be marked as not cached
            // so that the client will wait for the container completion
            return new BuildTrack(ret.request.mirrorId, ret.request.targetImage, ret.done(), ret.succeeded())
        }
        // invalid state
        throw new IllegalStateException("Unable to determine mirror status for '$request.targetImage'")
    }

    /**
     * {@inheritDoc}
     */
    @Override
    CompletableFuture<MirrorEntry> awaitCompletion(String targetImage) {
        return CompletableFuture.supplyAsync(()-> store.awaitCompletion(targetImage), ioExecutor)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    MirrorResult getMirrorResult(String mirrorId) {
        final entry = store.findByRequestId(mirrorId)
        return entry
                ? entry.result
                : persistence.loadMirrorResult(mirrorId)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    MirrorEntry getJobEntry(JobSpec jobSpec) {
        store.get(jobSpec.entryKey)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void onJobCompletion(JobSpec jobSpec, MirrorEntry entry, JobState jobState) {
        final result = entry.result.complete(jobState.exitCode, jobState.stdout)
        store.putEntry(entry.withResult(result))
        persistence.saveMirrorResult(result)
        scanService?.scanOnMirror(entry.withResult(result))
        log.debug "Mirror container completed - job=${jobSpec.operationName}; result=${result}; state=${jobState}"
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void onJobTimeout(JobSpec jobSpec, MirrorEntry entry) {
        final result = entry.result.complete(null, "Container mirror timed out")
        store.putEntry(entry.withResult(result))
        persistence.saveMirrorResult(result)
        log.warn "Mirror container timed out - job=${jobSpec.operationName}; result=${result}"
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void onJobException(JobSpec job, MirrorEntry entry, Throwable error) {
        final result = entry.result.complete(null, error.message)
        store.putEntry(entry.withResult(result))
        persistence.saveMirrorResult(result)
        log.error("Mirror container errored - job=${job.operationName}; result=${result}", error)
    }

}
