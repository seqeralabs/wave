package io.seqera.wave.service.scan

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.util.ThreadPoolBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements ContainerScanService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerScanServiceImpl implements ContainerScanService {

    @Inject
    private ContainerBuildServiceImpl containerBuildService

    @Inject
    private ScanStrategy scanStrategy

    @Inject
    private ScanConfig scanConfig

    private ExecutorService executor

    @Inject
    private PersistenceService persistenceService

    @Inject
    private CleanupStrategy cleanup

    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-scanner')
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            scan(ScanRequest.fromBuild(event.request))
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
        catch (Throwable t){
            log.error "Unable to launch the scan results for scanId : ${request.id}",t
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
            log.error "Unable to save results for scanId: ${scanResult.id}",t
        }
    }

}
