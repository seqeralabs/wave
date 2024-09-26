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

package io.seqera.wave.service.builder

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.core.RoutePath
import io.seqera.wave.service.buildstatus.BuildStatusService
import io.seqera.wave.service.persistence.WaveBuildRecord
/**
 * Declare container build service interface
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
interface ContainerBuildService {

    /**
     * Build a container image for the given {@link BuildRequest}
     *
     * @param request
     *      A {@link BuildRequest} modelling the build request
     * @return
     *      The container image where the resulting image is going to be hosted
     */
    BuildTrack buildImage(BuildRequest request)

    /**
     * Get a completable future that holds the build result
     *
     * @param targetImage
     *      the container repository name where the target image is expected to be retrieved once the
     *      build it complete
     * @return
     *      A completable future that holds the resulting {@link BuildResult} or
     *      {@code null} if not request has been submitted for such image
     */
    CompletableFuture<BuildResult> buildResult(String targetImage)

    default CompletableFuture<BuildResult> buildResult(BuildRequest request) {
        return buildResult(request.targetImage)
    }

    /**
     * Get a completable future that holds the build result
     *
     * @param route
     *      A {@link RoutePath} instance representing the container request
     * @return
     *      A completable future that holds the resulting {@link BuildResult} or
     *      {@code null} if not request has been submitted for such image
     */
    default CompletableFuture<BuildResult> buildResult(RoutePath route) {
        return route.request?.containerImage && route.isUnresolvedManifest()
                ? buildResult(route.request.containerImage)
                : null
    }


    // **************************************************************
    // **               build record operations
    // **************************************************************

    /**
     * Retrieve the build record for the specified id.
     *
     * @param buildId The ID of the build record to be retrieve
     * @return The {@link WaveBuildRecord} associated with the corresponding Id, or {@code null} if it cannot be found
     */
    WaveBuildRecord getBuildRecord(String buildId)

    /**
     * Retrieve the latest build record available for the specified container id.
     *
     * @param containerId The ID of the container for which the build record needs to be retrieve
     * @return The {@link WaveBuildRecord} associated with the corresponding Id, or {@code null} if it cannot be found
     */
    WaveBuildRecord getLatestBuild(String containerId)

    /**
     * The current status of the build request
     *
     * @param buildId
     *      The Id of the build request
     * @return
     *      The {@link BuildStatusResponse} object representing the build status of {@code null}
     *      if it cannot be found
     */
    BuildStatusService.StatusInfo getBuildStatus(String buildId)
}
