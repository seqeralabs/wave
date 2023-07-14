package io.seqera.wave.service

import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.persistence.WaveScanRecord
/**
 * Declare operations to scan containers
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface ContainerScanService {

    void scan(BuildRequest buildRequest, BuildResult buildResult)

    WaveScanRecord getScanResult(String buildId)
}
