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

package io.seqera.wave.service.scan

import java.nio.file.NoSuchFileException
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.api.ScanMode
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.builder.BuildFormat.DOCKER
/**
 * Implements ContainerScanService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Named("Scan")
@Requires(bean = ScanConfig)
@Singleton
@CompileStatic
class ContainerScanServiceImpl implements ContainerScanService, JobHandler<ScanEntry> {

    @Inject
    private ScanConfig config

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Inject
    private ScanStateStore scanStore

    @Inject
    private PersistenceService persistenceService

    @Inject
    private JobService jobService

    @Inject
    private ScanIdStore scanIdStore

    @Inject
    private ContainerInspectService inspectService

    ContainerScanServiceImpl() {}

    @Override
    String getScanId(String targetImage, ScanMode mode, String format) {
        // ignore singularity images
        if( format == 'sif' )
            return null
        // skip if scan mode is empty
        if( !mode )
            return null
        // create a new scan id if there's no entry for the given target image
        final result = scanIdStore
                .putIfAbsentAndCount(targetImage, ScanId.of(targetImage))
        // if the put is successful, update the scan id with count value
        // returned in the put operation
        if( result.v1 ) {
            final count = result.v3
            final ScanId scan = result.v2.withCount(count)
            scanIdStore.put(targetImage, scan)
            return scan
        }
        // use the existing scan id 
        return result.v2
    }

    @Override
    void scanOnBuild(BuildEvent event) {
        try {
            if( event.request.scanId && event.result.succeeded() && event.request.format == DOCKER ) {
                scan(fromBuild(event.request))
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - image=${event.request.targetImage}; reason=${e.message?:e}"
        }
    }

    @Override
    void scanOnMirror(MirrorEntry event) {
        try {
            if( event.request.scanId && event.result.succeeded() ) {
                scan(fromMirror(event.request))
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - image=${event.request.targetImage}; reason=${e.message?:e}"
        }
    }

    @Override
    void scanOnRequest(ContainerRequest request) {
        try {
            if( request.scanId  ) {
                scan(fromContainer(request))
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - image=${request.containerImage}; reason=${e.message?:e}"
        }
    }

    @Override
    void scan(ScanRequest request) {
        //start scanning of build container
        CompletableFuture
                .runAsync(() -> launch(request), executor)
    }

    @Override
    WaveScanRecord getScanResult(String scanId) {
        try{
            final scan = scanStore.getScan(scanId)
            return scan
                    ? new WaveScanRecord(scan.scanId, scan)
                    : persistenceService.loadScanRecord(scanId)
        }
        catch (Throwable t){
            log.error("Unable to load the scan result - id=${scanId}", t)
             return null
        }
    }

    protected void launch(ScanRequest request) {
        try {
            // create a record to mark the beginning
            final scan = ScanEntry.pending(request.scanId, request.requestId, request.targetImage)
            if( scanStore.putIfAbsent(scan.scanId, scan) ) {
                // launch container scan
                jobService.launchScan(request)
            }
        }
        catch (Throwable e){
            log.warn "Unable to save scan result - id=${request.scanId}; cause=${e.message}", e
            updateScanEntry(ScanEntry.failure(request))
        }
    }

    protected ScanRequest fromBuild(BuildRequest request) {
        final workDir = request.workDir.resolveSibling(request.scanId)
        return new ScanRequest(
                request.scanId,
                request.buildId,
                request.configJson,
                request.targetImage,
                request.platform,
                workDir,
                Instant.now())
    }

    protected ScanRequest fromMirror(MirrorRequest request) {
        final workDir = request.workDir.resolveSibling(request.scanId)
        return new ScanRequest(
                request.scanId,
                request.mirrorId,
                request.authJson,
                request.targetImage,
                request.platform,
                workDir,
                Instant.now())
    }

    protected ScanRequest fromContainer(ContainerRequest request) {
        final workDir = config.workspace.resolve(request.scanId)
        final authJson = inspectService.credentialsConfigJson(null, request.containerImage, null, request.identity)
        return new ScanRequest(
                request.scanId,
                request.requestId,
                authJson,
                request.containerImage,
                request.platform,
                workDir,
                Instant.now())
    }

    // **************************************************************
    // **               scan job handle implementation
    // **************************************************************

    @Override
    ScanEntry getJobEntry(JobSpec job) {
        scanStore.getScan(job.entryKey)
    }

    @Override
    void onJobCompletion(JobSpec job, ScanEntry entry, JobState state) {
        ScanEntry result
        if( state.succeeded() ) {
            try {
                result = entry.success(TrivyResultProcessor.process(job.workDir.resolve(Trivy.OUTPUT_FILE_NAME)))
                log.info("Container scan succeeded - id=${entry.scanId}; exit=${state.exitCode}; stdout=${state.stdout}")
            }
            catch (NoSuchFileException e) {
                result = entry.failure(0, "No such file: ${e.message}")
                log.warn("Container scan failed - id=${entry.scanId}; exit=${state.exitCode}; stdout=${state.stdout}; exception: NoSuchFile=${e.message}")
            }
        }
        else{
            result = entry.failure(state.exitCode, state.stdout)
            log.warn("Container scan failed - id=${entry.scanId}; exit=${state.exitCode}; stdout=${state.stdout}")
        }

        updateScanEntry(result)
    }

    @Override
    void onJobException(JobSpec job, ScanEntry entry, Throwable e) {
        log.error("Container scan exception - id=${entry.scanId} - cause=${e.getMessage()}", e)
        updateScanEntry(entry.failure(null, e.message))
    }

    @Override
    void onJobTimeout(JobSpec job, ScanEntry entry) {
        log.warn("Container scan timed out - id=${entry.scanId}")
        updateScanEntry(entry.failure(null, "Container scan timed out"))
    }

    protected void updateScanEntry(ScanEntry scan) {
        try{
            //save scan results in the redis cache
            scanStore.put(scan.scanId, scan)
            // save in the persistent layer
            persistenceService.updateScanRecord(new WaveScanRecord(scan.scanId, scan))
        }
        catch (Throwable t){
            log.error("Unable to save result - id=${scan.scanId}; cause=${t.message}", t)
        }
    }

}
