package io.seqera.wave.configuration

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

@CompileStatic
@Singleton
@Slf4j
class ContainerScanConfig {
    @Value('${wave.scanner.image.name:aquasec/trivy}')
    String scannerImage
    @PostConstruct
    private void init() {
        log.debug("Scanner config : docker image name : ${scannerImage}")
    }
}
