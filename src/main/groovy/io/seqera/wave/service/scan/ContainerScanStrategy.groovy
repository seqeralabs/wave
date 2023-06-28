package io.seqera.wave.service.scan

import groovy.util.logging.Slf4j

@Slf4j
abstract class ContainerScanStrategy {
    abstract String scanContainer(String containerScanner, String containerName);
}
