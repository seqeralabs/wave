package io.seqera.wave.service.scan

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener;
import io.seqera.wave.service.ContainerScanService
import io.seqera.wave.service.builder.BuildEvent;
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
    ContainerScanStrategy containerScanStrategy;

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            scan(event.request.getTargetImage())
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - reason: ${e.message?:e}"
        }
    }

    @Override
    String scan(String containerName) {
        log.info("started scanning of "+containerName)
        return containerScanStrategy.scanContainer(containerName)
    }
}
