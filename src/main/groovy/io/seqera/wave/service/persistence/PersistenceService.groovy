package io.seqera.wave.service.persistence

import groovy.transform.CompileStatic
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.model.ScanVulnerability

/**
 * A storage for statistic data
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
interface PersistenceService {

    @EventListener
    default void onBuildEvent(BuildEvent event) {
        saveBuild(WaveBuildRecord.fromEvent(event))
    }

    /**
     * Store a {@link WaveBuildRecord} object in the underlying persistence layer.
     *
     * It maye be implemented in non-blocking manner therefore there's no guarantee
     * the record is accessible via #loadBuild immediately after this operation
     *
     * @param build A {@link WaveBuildRecord} object
     */
    void saveBuild(WaveBuildRecord build)

    /**
     * Retrieve a {@link WaveBuildRecord} object for the given build id
     *
     * @param buildId The build id i.e. the checksum of dockerfile + condafile + repo
     * @return The corresponding {@link WaveBuildRecord} object object
     */
    WaveBuildRecord loadBuild(String buildId)

    /**
     * Store a {@link WaveContainerRecord} object in the Surreal wave_request table.
     *
     * @param token The request token associated with this request
     * @param data A {@link WaveContainerRecord} object representing a Wave request record
     */
    void saveContainerRequest(String token, WaveContainerRecord data)

    /**
     * Update a container request with the digest of the resolved request
     *
     * @param token The request unique token
     * @param digest The resolved digest
     */
    void updateContainerRequest(String token, ContainerDigestPair digest)

    /**
     * Retrieve a {@link WaveContainerRecord} object corresponding to the a specified request token
     *
     * @param token The token for which recover the corresponding {@link WaveContainerRecord} object
     * @return The {@link WaveContainerRecord} object associated with the corresponding token or {@code null} otherwise
     */
    WaveContainerRecord loadContainerRequest(String token)

    /**
     * Store a {@link WaveContainerScanRecord} object in the Surreal wave_request table.
     *
     * @param buildId The request token associated with this request
     * @param data A {@link WaveContainerScanRecord} object representing a Wave request record
     */
    void saveContainerScanResult(String buildId, WaveContainerScanRecord waveContainerScanRecord, List<ScanVulnerability> scanVulnerabilities)

    /**
     * Retrieve a {@link WaveContainerScanRecord} object corresponding to the a specified request token
     *
     * @param buildId The token for which recover the corresponding {@link WaveContainerScanRecord} object
     * @return The {@link WaveContainerScanRecord} object associated with the corresponding token or {@code null} otherwise
     */
    WaveContainerScanRecord loadContainerScanResult(String buildId)

    /**
     * Retrieve a {@link WaveContainerScanRecord} object corresponding to the a specified request token
     *
     * @param buildId The token for which recover the corresponding {@link List<ScanVulnerability>} object
     * @return The {@link @link List<ScanVulnerability>} object associated with the corresponding token or {@code null} otherwise
     */
    ScanResult loadContainerScanVulResult(String buildId)

}
