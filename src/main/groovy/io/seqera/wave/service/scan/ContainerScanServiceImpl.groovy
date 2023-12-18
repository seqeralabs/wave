/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.service.cleanup.CleanupStrategy
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
@Requires(property = 'wave.scan.enabled', value = 'true')
@Singleton
@CompileStatic
class ContainerScanServiceImpl implements ContainerScanService {

    @Inject
    private ContainerBuildServiceImpl containerBuildService

    @Inject
    private ScanStrategy scanStrategy

    @Inject
    private ScanConfig scanConfig

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Inject
    private PersistenceService persistenceService

    @Inject
    private CleanupStrategy cleanup

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            if( event.result.succeeded() && event.request.format == DOCKER ) {
                scan(ScanRequest.fromBuild(event.request))
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - reason: ${e.message?:e}"
        }
    }

    @Override
    void scan(ScanRequest request) {
        //start scanning of build container
        CompletableFuture
                .supplyAsync(() -> launch(request), executor)
                .thenAcceptAsync((scanResult) -> completeScan(scanResult), executor)
    }

    @Override
    WaveScanRecord getScanResult(String scanId) {
        try{
            return persistenceService.loadScanRecord(scanId)
        }catch (Throwable t){
            log.error "Unable to load the scan results for scanId: ${scanId}", t
             return null
        }
    }

    protected ScanResult launch(ScanRequest request) {
        ScanResult scanResult = null
        try {
            // create a record to mark the beginning
            persistenceService.createScanRecord(new WaveScanRecord(request.id, request.buildId, Instant.now()))
            //launch container scan
            scanResult = scanStrategy.scanContainer(request)
        }
        catch (Throwable e){
            log.warn "Unable to launch the scan results for scan id: ${request.id} - cause: ${e.message}", e
        }
        finally{
            // cleanup build context
            if( cleanup.shouldCleanup(scanResult?.isSucceeded() ? 0 : 1) )
                request.workDir?.deleteDir()
        }
        return scanResult
    }

    protected void completeScan(ScanResult scanResult) {
        try{
            //save scan results
            persistenceService.updateScanRecord(new WaveScanRecord(scanResult.id, scanResult))
        }
        catch (Throwable t){
            log.error "Unable to save results for scan id: ${scanResult.id}", t
        }
    }

}
