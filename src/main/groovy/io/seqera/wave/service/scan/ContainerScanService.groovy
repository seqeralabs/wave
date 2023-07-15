package io.seqera.wave.service.scan


import io.seqera.wave.service.persistence.WaveScanRecord
/**
 * Declare operations to scan containers
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface ContainerScanService {

    void scan(ScanRequest request)

    WaveScanRecord getScanResult(String scanId)
}
