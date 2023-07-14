package io.seqera.wave.service.scan


import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.ContainerScanService
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerScanRecord
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
    ContainerBuildServiceImpl containerBuildService

    @Inject
    ScanStrategy containerScanStrategy

    @Inject
    ScanConfig containerScanConfig


    private ExecutorService executor

    @Inject
    private PersistenceService persistenceService

    @Inject CleanupStrategy cleanup

    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-scanner')
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            scan(event.request, event.result)
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - reason: ${e.message?:e}"
        }
    }

    @Override
    void scan(BuildRequest buildRequest, BuildResult buildResult) {
        //start scanning of build container
        CompletableFuture
                .supplyAsync(() -> launch(buildRequest, buildResult), executor)
                .thenApply((result) -> {completeScan(buildRequest,result); return result})
    }

    @Override
    WaveContainerScanRecord getScanResult(String buildId) {
        return persistenceService.loadContainerScanResult(buildId)
    }

    ScanResult launch(BuildRequest buildRequest, BuildResult buildResult){
        ScanResult scanResult = null
        try {
            //launch container scan
            scanResult = containerScanStrategy.scanContainer(containerScanConfig.scannerImage, buildRequest)
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

    void completeScan(BuildRequest buildRequest, ScanResult scanResult){
        try{
            //save scan results
            persistenceService.saveContainerScanResult(buildRequest.id, new WaveContainerScanRecord(buildRequest.id,scanResult), scanResult.vulnerabilities)
        }
        catch (Exception e){
            log.warn "Unable to save the scan results for build: ${buildRequest.id}",e
        }
    }

}
