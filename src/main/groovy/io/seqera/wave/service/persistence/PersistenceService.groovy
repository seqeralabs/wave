/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.persistence

import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.scan.ScanResult
/**
 * A storage for statistic data
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
interface PersistenceService {

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
     * Retrieve a {@link WaveBuildRecord} object for the given target image and container digest
     *
     * @param targetImage The container target image name e.g. docker.io/user/image:tag
     * @param digest The container image sha256 checksum
     * @return The corresponding {@link WaveBuildRecord} object or {@code null} if no record is found
     */
    WaveBuildRecord loadBuild(String targetImage, String digest)

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
     * Create a scan record, this signal that a container scan request has been created
     *
     * @param scanRecord Create a record with the object specified
     */
    void createScanRecord(WaveScanRecord scanRecord)

    /**
     * Store a {@link WaveScanRecord} object in the Surreal wave_scan table.
     *
     * @param data A {@link WaveScanRecord} object representing the a container scan request
     */
    void updateScanRecord(WaveScanRecord scanRecord)

    /**
     * Retrieve a {@link WaveScanRecord} object for the specified build ID
     *
     * @param buildId The ID of the build for which load the scan record
     * @return The {@link WaveScanRecord} object for the specified build ID or null otherwise
     */
    WaveScanRecord loadScanRecord(String scanId)

    /**
     * Retrieve a {@link ScanResult} object for the specified build ID
     *
     * @param buildId The ID of the build for which load the scan result
     * @return The {@link ScanResult} object associated with the specified build ID or throws the exception {@link NotFoundException} otherwise
     * @throws NotFoundException If the a record for the specified build ID cannot be found
     */
    default ScanResult loadScanResult(String scanId) {
        final scanRecord = loadScanRecord(scanId)
        if( !scanRecord )
            throw new NotFoundException("No scan report exists with id: ${scanId}")

        return ScanResult.create(
                scanRecord.id,
                scanRecord.buildId,
                scanRecord.startTime,
                scanRecord.duration,
                scanRecord.status,
                scanRecord.vulnerabilities )
    }

    /**
     * Retrieve a {@link List<WaveBuildRecord>} List of objects matching the specified image name and user
     *
     * @param imageName The container image name
     * @param user The user name or email
     * @return The corresponding {@link WaveBuildRecord} object object
     */
    List<WaveBuildRecord> loadBuilds(String imageName, String user)
}
