package io.seqera.wave.configuration

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
/**
 * Container Scan service settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class ContainerScanConfig {
    @Value('${wave.scanner.image.name:aquasec/trivy:0.43.0}')
    String scannerImage
    @PostConstruct
    private void init() {
        log.debug("Scanner config : docker image name : ${scannerImage}")
    }
}
