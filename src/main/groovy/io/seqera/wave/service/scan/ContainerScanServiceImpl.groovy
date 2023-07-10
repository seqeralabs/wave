package io.seqera.wave.service.scan

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.configuration.ContainerScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.ContainerScanService
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStrategy
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
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
    BuildStrategy buildStrategy

    @Inject
    ContainerScanStrategy containerScanStrategy

    @Inject
    ContainerScanConfig containerScanConfig


    private ExecutorService executor

    @Inject
    private PersistenceService persistenceService


    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-scanner')
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            if (event.result.exitStatus == 0) {
                scan(event.request, event.result)
            } else {
                // cleanup build context
                cleanup(event.request, event.result)
            }
        }catch (Exception e) {
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
        }catch (Exception e){
            log.warn "Unable to launch the scan results for build : ${buildRequest.id}",e
        }finally{
            // cleanup build context
            cleanup(buildRequest,buildResult)
        }
        return scanResult
    }

    void completeScan(BuildRequest buildRequest, ScanResult scanResult){
        try{
            //save scan results
            scanResult.isCompleted = true
            persistenceService.saveContainerScanResult(buildRequest.id, new WaveContainerScanRecord(buildRequest.id,scanResult))
        }catch (Exception e){
            log.warn "Unable to save the scan results for build : ${buildRequest.id}",e
        }
    }

    void cleanup(BuildRequest buildRequest, BuildResult buildResult){
        if( containerBuildService.shouldCleanup(buildResult) )
            buildStrategy.cleanup(buildRequest)
    }
}
