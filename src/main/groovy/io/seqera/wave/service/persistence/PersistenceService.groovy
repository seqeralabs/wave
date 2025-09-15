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

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.postgres.data.ImageRow

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
    CompletableFuture<Void> saveBuildAsync(WaveBuildRecord build)

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
    WaveBuildRecord loadBuildSucceed(String targetImage, String digest)

    /**
     * Retrieve the latest {@link WaveBuildRecord} object for the given container id
     *
     * @param containerId The container id for which the latest build record should be retrieved
     * @return The corresponding {@link WaveBuildRecord} object or {@code null} if no record is found
     */
    WaveBuildRecord latestBuild(String containerId)

    /**
     * Retrieve all {@link WaveBuildRecord} object for the given container id
     *
     * @param containerId The container id for which all the builds record should be retrieved
     * @return The corresponding {@link WaveBuildRecord} object or {@code null} if no record is found
     */
    List<WaveBuildRecord> allBuilds(String containerId)

    /**
     * Store a {@link WaveContainerRecord} object in the Surreal wave_request table.
     *
     * @param data A {@link WaveContainerRecord} object representing a Wave request record
     */
    CompletableFuture<Void> saveContainerRequestAsync(WaveContainerRecord data)

    /**
     * Update a container request with the digest of the resolved request
     *
     * @param token The request unique token
     * @param digest The resolved digest
     */
    CompletableFuture<Void> updateContainerRequestAsync(String token, ContainerDigestPair digest)

    /**
     * Retrieve a {@link WaveContainerRecord} object corresponding to the a specified request token
     *
     * @param token The token for which recover the corresponding {@link WaveContainerRecord} object
     * @return The {@link WaveContainerRecord} object associated with the corresponding token or {@code null} otherwise
     */
    WaveContainerRecord loadContainerRequest(String token)

    /**
     * Store a {@link WaveScanRecord} object in the Surreal wave_scan table.
     *
     * @param data A {@link WaveScanRecord} object representing the a container scan request
     */
    CompletableFuture<Void> saveScanRecordAsync(WaveScanRecord scanRecord)

    /**
     * Retrieve a {@link WaveScanRecord} object for the specified build ID
     *
     * @param buildId The ID of the build for which load the scan record
     * @return The {@link WaveScanRecord} object for the specified build ID or null otherwise
     */
    WaveScanRecord loadScanRecord(String scanId)

    /**
     * Check if a scan record exist
     *
     * @param scanId The Id of the scan to check
     * @return {@code true} if the scan record with the specified id exists or {@code false} otherwise
     */
    boolean existsScanRecord(String scanId)

    /**
     * Load a mirror state record
     *
     * @param mirrorId The ID of the mirror record
     * @return The corresponding {@link MirrorEntry} object or null if it cannot be found
     */
    MirrorResult loadMirrorResult(String mirrorId)

    /**
     * Load a mirror state record given the target image name and the image digest.
     * It returns the latest succeed mirror result.
     *
     * @param targetImage The target mirrored image name
     * @param digest The image content SHA256 digest
     * @return The corresponding {@link MirrorEntry} object or null if it cannot be found
     */
    MirrorResult loadMirrorSucceed(String targetImage, String digest)

    /**
     * Persists a {@link MirrorEntry} state record
     *
     * @param mirror {@link MirrorEntry} object
     */
    CompletableFuture<Void> saveMirrorResultAsync(MirrorResult mirror)

    /**
     * Retrieve all {@link WaveScanRecord} object for the given partial scan id
     *
     * @param scanId The scan id for which all the scan records should be retrieved
     * @return The corresponding {@link WaveScanRecord} object or {@code null} if no record is found
     */
    List<WaveScanRecord> allScans(String scanId)

    /**
     * Retrieve the latest {@link WaveScanRecord} object for the given container image name
     * where the build status is 'SUCCEED'
     *
     * @param image The container image name for which the latest successful build record should be retrieved
     * @return The corresponding {@link WaveScanRecord} object or {@code null} if no record is found
     */
    WaveScanRecord loadScanLatestSucceed(String image)

    /**
     * Save an image record
     *
     * @param imageRow The {@link ImageRow} object to be saved
     */
    void saveImageAsync(ImageRow imageRow)

    /**
     * Save an image record
     *
     * @param imageRow The {@link ImageRow} object to be saved
     */
    ImageRow loadImage(String id)

}
