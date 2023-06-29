package io.seqera.wave.service

import io.seqera.wave.service.builder.BuildRequest;
/**
 * Declare operations to scan containers
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface ContainerScanService {
    void scan(BuildRequest buildRequest)
    String getScanResult(String buildId)
}
