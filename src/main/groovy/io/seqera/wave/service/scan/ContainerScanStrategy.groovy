package io.seqera.wave.service.scan

import groovy.util.logging.Slf4j
import io.seqera.wave.service.builder.BuildRequest

@Slf4j
abstract class ContainerScanStrategy {
    abstract String scanContainer(String containerScanner, BuildRequest buildRequest);
}
