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

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.api.ScanMode
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.configuration.ScanEnabled
import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.cleanup.CleanupService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.SCAN_REPORTS
import static io.seqera.wave.service.builder.BuildFormat.DOCKER
import static io.seqera.wave.service.job.JobHelper.saveDockerAuth
/**
 * Implements ContainerScanService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Named("Scan")
@Requires(bean = ScanEnabled)
@Singleton
@CompileStatic
class ContainerScanServiceImpl implements ContainerScanService, JobHandler<ScanEntry> {

    @Inject
    private ScanConfig config

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

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

    @Inject
    private MetricsService metricsService

    @Inject
    private CleanupService cleanupService

    @Inject
    @Nullable
    private ScanStrategy scanStrategy

    @Inject
    @Named(SCAN_REPORTS)
    private ObjectStorageOperations<?, ?, ?> scanStoreOpts

    ContainerScanServiceImpl() {}

    @Override
    String getScanId(String targetImage, String digest, ScanMode mode, String format) {
        // ignore singularity images
        if( format == 'sif' )
            return null
        // skip if scan mode is empty
        if( !mode )
            return null
        // create a new scan id if there's no entry for the given target image
        final result = scanIdStore
                .putIfAbsentAndCount(targetImage, ScanId.of(targetImage, digest))
        // return the value updated by the put operation
        return result.value.toString()
    }

    @Override
    void scanOnBuild(BuildEntry entry) {
        try {
            if( entry.request.scanId && entry.result.succeeded() && entry.request.format == DOCKER ) {
                scan(fromBuild(entry.request))
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - image=${entry.request.targetImage}; reason=${e.message?:e}"
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
            if( request.scanId && request.scanMode && request.isContainer() && !request.dryRun ) {
                log.debug "Container scan required by scanOnRequest=$request"
                scan(fromContainer(request))
            }
            else if( request.scanId && request.scanMode && !request.isContainer() && request.buildNew==false && request.succeeded && !request.dryRun && !existsScan(request.scanId) ) {
                log.debug "Container scan required by cached request=$request"
                scan(fromContainer(request))
            }
            else {
                log.debug "Container scan NOT required by request=$request"
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - image=${request.containerImage}; reason=${e.message?:e}"
        }
    }

    @Override
    void scan(ScanRequest request) {
        try {
            // create a record to mark the beginning
            final scan = ScanEntry.create(request)
            if( scanStore.putIfAbsent(scan.scanId, scan) ) {
                //start scanning of build container
                CompletableFuture.runAsync(() -> launch(request), ioExecutor)
            }
        }
        catch (Throwable e){
            log.warn "Unable to save scan result - id=${request.scanId}; cause=${e.message}", e
            storeScanEntry(ScanEntry.failure(request))
        }
    }

    @Override
    ScanEntry getScanState(String scanId) {
        return scanStore.get(scanId)
    }

    boolean existsScan(String scanId) {
        return scanStore.get(scanId) ?: persistenceService.existsScanRecord(scanId)
    }

    @Override
    WaveScanRecord getScanRecord(String scanId) {
        try{
            final scan = scanStore.getScan(scanId)
            return scan
                    ? new WaveScanRecord(scan)
                    : persistenceService.loadScanRecord(scanId)
        }
        catch (Throwable t){
            log.error("Unable to load the scan result - id=${scanId}", t)
             return null
        }
    }

    protected void launch(ScanRequest request) {
        try {
            incrScanMetrics(request)
            jobService.launchScan(request)
        }
        catch (Throwable e){
            log.warn "Unable to save scan result - id=${request.scanId}; cause=${e.message}", e
            storeScanEntry(ScanEntry.failure(request))
        }
    }

    protected void incrScanMetrics(ScanRequest request) {
        try {
            //increment metrics
            metricsService.incrementScansCounter(request.identity, request.platform.arch)
        }
        catch (Throwable e) {
            log.warn "Enable to increase scan metrics - cause: ${e.cause}", e
        }
    }
    
    protected ScanRequest fromBuild(BuildRequest request) {
        final workDir = request.workDir.resolveSibling(request.scanId)
        return new ScanRequest(
                request.scanId,
                request.buildId,
                null,
                null,
                request.configJson,
                request.targetImage,
                request.platform,
                workDir,
                Instant.now(),
                request.identity)
    }

    protected ScanRequest fromMirror(MirrorRequest request) {
        final workDir = request.workDir.resolveSibling(request.scanId)
        return new ScanRequest(
                request.scanId,
                null,
                request.mirrorId,
                null,
                request.authJson,
                request.targetImage,
                request.platform,
                workDir,
                Instant.now(),
                request.identity)
    }

    protected ScanRequest fromContainer(ContainerRequest request) {
        final workDir = config.workspace.resolve(request.scanId)
        final authJson = inspectService.credentialsConfigJson(null, request.containerImage, null, request.identity)
        return new ScanRequest(
                request.scanId,
                !request.mirror ? request.buildId : null,
                request.mirror ? request.buildId : null,
                request.requestId,
                authJson,
                request.containerImage,
                request.platform,
                workDir,
                Instant.now(),
                request.identity)
    }

    // **************************************************************
    // **               scan job handle implementation
    // **************************************************************

    @Override
    ScanEntry getJobEntry(JobSpec job) {
        scanStore.getScan(job.entryKey)
    }

    @Override
    JobSpec launchJob(JobSpec job, ScanEntry entry) {
        if( !scanStrategy )
            throw new IllegalStateException("Security scan service is not available - check configuration setting 'wave.scan.enabled'")
        // save docker auth file
        saveDockerAuth(entry.workDir, entry.configJson)
        // launch scan job
        if( !entry.platform )
            scanStrategy.scanPlugin(job.operationName, entry)
        else
            scanStrategy.scanContainer(job.operationName, entry)
        // return the update job
        return job.withLaunchTime(Instant.now())
    }

    @Override
    void onJobCompletion(JobSpec job, ScanEntry entry, JobState state) {
        ScanEntry result
        if( state.succeeded() ) {
            try {
                final scanFile = job.workDir.resolve(ScanType.Default.output)
                final vulnerabilities = TrivyResultProcessor.parseFile(scanFile, config.vulnerabilityLimit)
                result = entry.success(vulnerabilities)
                uploadScanReports(entry.scanId, job.workDir.resolve(ScanType.Spdx.output))
                uploadScanReports(entry.scanId, job.workDir.resolve(ScanType.CycloneDx.output))
                log.info("Container scan succeeded - id=${entry.scanId}; exit=${state.exitCode}; stdout=${state.stdout}")
            }
            catch (NoSuchFileException e) {
                result = entry.failure(0, "No such file: ${e.message}")
                log.warn("Container scan failed - id=${entry.scanId}; NoSuchFile=${e.message}")
            }
        }
        else{
            result = entry.failure(state.exitCode, state.stdout)
            log.warn("Container scan failed - id=${entry.scanId}; exit=${state.exitCode}; stdout=${state.stdout}")
        }

        storeScanEntry(result)
    }

    protected void uploadScanReports(String scanId, Path data) {
        if( !config.reportsPath || !Files.exists(data) )
            return
        final uploadRequest = UploadRequest.fromBytes(data.bytes, scanKey(scanId, data.fileName.toString()))
        CompletableFuture.runAsync(()-> scanStoreOpts.upload(uploadRequest), ioExecutor)
    }

    protected String scanKey(String scanId, String fileName) {
        if( !scanId )
            return null
        final name = "${scanId}/${fileName}"
        final prefix = config.reportsPrefix
        return prefix
                ? "${prefix}/${name}"
                : name
    }

    @Override
    void onJobException(JobSpec job, ScanEntry entry, Throwable e) {
        log.error("Container scan exception - id=${entry.scanId} - cause=${e.getMessage()}", e)
        storeScanEntry(entry.failure(null, e.message))
    }

    @Override
    void onJobTimeout(JobSpec job, ScanEntry entry) {
        log.warn("Container scan timed out - id=${entry.scanId}")
        storeScanEntry(entry.failure(null, "Container scan timed out"))
    }

    protected void storeScanEntry(ScanEntry scan) {
        try{
            //save scan results in the redis cache
            scanStore.storeScan(scan)
            // save in the persistent layer
            persistenceService.saveScanRecordAsync(new WaveScanRecord(scan))
            // when the scan fails delete the scanId via cleanup service
            // this is needed to prevent the caching of the scanId and
            // allow re-scanning the container in case of a job failure
            if( scan.done() && !scan.succeeded() ) {
                cleanupService.cleanupScanId(scan.containerImage)
            }
        }
        catch (Throwable t){
            log.error("Unable to save result - id=${scan.scanId}; cause=${t.message}", t)
        }
    }

    @Override
    List<WaveScanRecord> getAllScans(String scanId){
        persistenceService.allScans(scanId)
    }

    @Override
    StreamedFile fetchReportStream(String scanId, ScanType type) {
        if( !scanId ) return null
        final Optional<ObjectStorageEntry<?>> result = scanStoreOpts.retrieve(scanKey(scanId,type.output))
        return result.isPresent() ? result.get().toStreamedFile() : null
    }

    @Override
    void scanPlugin(String plugin) {
        try {
            final scanId = getScanId(plugin, null, ScanMode.required, null)
            final workDir = config.workspace.resolve(scanId)

            log.info("Starting plugin scan - pluginUrl=${plugin}; scanId=${scanId}")
            // Create a custom scan request for plugin scanning
            final pluginScanRequest = ScanRequest.of( scanId:  scanId, targetImage:  plugin, workDir:  workDir, platform: null)

            // Create scan entry
            scan(pluginScanRequest)
        }
        catch (Exception e) {
            log.warn "Unable to run plugin scan - pluginUrl=${plugin}; reason=${e.message}"
            println(e.stackTrace)
        }
    }

}
