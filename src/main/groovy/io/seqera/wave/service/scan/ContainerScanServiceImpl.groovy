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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveScanRecord
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
@Requires(property = 'wave.scan.enabled', value = 'true')
@Singleton
@CompileStatic
class ContainerScanServiceImpl implements ContainerScanService, JobHandler<ScanState> {

    @Inject
    private ScanConfig scanConfig

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Inject
    private ScanStore scanStore

    @Inject
    private PersistenceService persistenceService

    @Inject
    private JobService jobService


    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            if( event.result.succeeded() && event.request.format == DOCKER ) {
                scan(ScanRequest.fromBuild(event.request))
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - image=${event.request.targetImage}; reason=${e.message?:e}"
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
            final scan = ScanState.pending(request.scanId, request.buildId, request.targetImage)
            scanStore.put(scan.scanId, scan)
            //launch container scan
            jobService.launchScan(request)
        }
        catch (Throwable e){
            log.warn "Unable to save scan result - id=${request.scanId}; cause=${e.message}", e
            updateScanRecord(ScanState.failure(request))
        }
    }


    // **************************************************************
    // **               scan job handle implementation
    // **************************************************************

    @Override
    ScanState getJobEntry(JobSpec job) {
        scanStore.getScan(job.entryKey)
    }

    @Override
    void onJobCompletion(JobSpec job, ScanState scan, JobState state) {
        ScanState result
        if( state.succeeded() ) {
            try {
                result = scan.success(TrivyResultProcessor.process(job.workDir.resolve(Trivy.OUTPUT_FILE_NAME)))
                log.info("Container scan succeeded - id=${scan.scanId}; exit=${state.exitCode}; stdout=${state.stdout}")
            }
            catch (NoSuchFileException e) {
                result = scan.failure(0, "No such file: ${e.message}")
                log.warn("Container scan failed - id=${scan.scanId}; exit=${state.exitCode}; stdout=${state.stdout}; exception: NoSuchFile=${e.message}")
            }
        }
        else{
            result = scan.failure(state.exitCode, state.stdout)
            log.warn("Container scan failed - id=${scan.scanId}; exit=${state.exitCode}; stdout=${state.stdout}")
        }

        updateScanRecord(result)
    }

    @Override
    void onJobException(JobSpec job, ScanState scan, Throwable e) {
        log.error("Container scan exception - id=${scan.scanId} - cause=${e.getMessage()}", e)
        updateScanRecord(scan.failure(null, e.message))
    }

    @Override
    void onJobTimeout(JobSpec job, ScanState scan) {
        log.warn("Container scan timed out - id=${scan.scanId}")
        updateScanRecord(scan.failure(null, "Container scan timed out"))
    }

    protected void updateScanRecord(ScanState scan) {
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
