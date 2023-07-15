package io.seqera.wave.service.scan

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
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
            scan(event.request)
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - reason: ${e.message?:e}"
        }
    }

    @Override
    void scan(BuildRequest buildRequest) {
        //start scanning of build container
        CompletableFuture
                .supplyAsync(() -> launch(buildRequest), executor)
                .thenAcceptAsync((scanResult) -> completeScan(scanResult), executor)
    }

    @Override
    WaveScanRecord getScanResult(String buildId) {
        return persistenceService.loadScanRecord(buildId)
    }

    protected  ScanResult launch(BuildRequest buildRequest) {
        ScanResult scanResult = null
        try {
            //launch container scan
            scanResult = scanStrategy.scanContainer(buildRequest)
        }
        catch (Exception e){
            log.warn "Unable to launch the scan results for build : ${buildRequest.id}",e
        }
        finally{
            // cleanup build context
            if( cleanup.shouldCleanup(scanResult?.isSuccess ? 0 : 1) )
                buildRequest.scanDir?.deleteDir()
        }
        return scanResult
    }

    protected void completeScan(ScanResult scanResult) {
        try{
            //save scan results
            persistenceService.saveContainerScanResult(scanResult.buildId, new WaveScanRecord(scanResult.buildId, scanResult))
        }
        catch (Exception e){
            log.warn "Unable to save the scan results for build: ${scanResult.buildId}",e
        }
    }

}
