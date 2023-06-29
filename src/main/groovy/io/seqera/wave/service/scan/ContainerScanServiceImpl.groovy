package io.seqera.wave.service.scan

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.configuration.ContainerScanConfig;
import io.seqera.wave.service.ContainerScanService
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerScanRecord
import io.seqera.wave.util.ThreadPoolBuilder;
import jakarta.inject.Inject
import jakarta.inject.Singleton;
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
            scan(event.request)
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - reason: ${e.message?:e}"
        }
    }

    @Override
    void scan(BuildRequest buildRequest) {
        CompletableFuture
                .supplyAsync(() -> containerScanStrategy.scanContainer(containerScanConfig.scannerImage, buildRequest), executor)
                .thenApply((result) ->
                { persistenceService.saveContainerScanResult(buildRequest.id, new WaveContainerScanRecord(buildRequest.id,result)); return result})
    }

    @Override
    String getScanResult(String buildId) {
        return persistenceService.loadContainerScanResult(buildId)
    }
}
