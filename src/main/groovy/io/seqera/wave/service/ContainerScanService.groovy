package io.seqera.wave.service;
/**
 * Declare operations to scan containers
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface ContainerScanService {
    void scan(String buildId, String containerName)
    String getScanResult(String buildId)
}
