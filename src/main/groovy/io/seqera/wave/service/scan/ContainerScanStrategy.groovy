package io.seqera.wave.service.scan

import groovy.util.logging.Slf4j
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildRequest

@Slf4j
abstract class ContainerScanStrategy {
    abstract ScanResult scanContainer(String containerScanner, BuildRequest buildRequest);
}
