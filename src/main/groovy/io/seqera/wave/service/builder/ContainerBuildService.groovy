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
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.core.RoutePath
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

    @EventListener
    default void onBuildEvent(BuildEvent event) {
        saveBuildRecord(event)
    }

    /**
     * Store a build record for the given {@link BuildRequest} object.
     *
     * This method is expected to store the build record associated with the request
     * *only* in the short term store caching system, ie. without hitting the
     * long-term SurrealDB storage
     *
     * @param request The build request that needs to be storage
     */
    default void createBuildRecord(BuildRequest request) {
        final record0 = WaveBuildRecord.fromEvent(new BuildEvent(request))
        createBuildRecord(record0.buildId, record0)
    }

    /**
     * Store the build record associated with the specified event both in the
     * short-term cache (redis) and long-term persistence layer (surrealdb)
     *
     * @param event The {@link BuildEvent} object for which the build record needs to be stored
     */
    default void saveBuildRecord(BuildEvent event) {
        final record0 = WaveBuildRecord.fromEvent(event)
        saveBuildRecord(record0.buildId, record0)
    }

    /**
     * Store a build record object.
     *
     * This method is expected to store the build record *only* in the short term store cache (redis),
     * ie. without hitting the long-term storage (surrealdb)
     *
     * @param buildId The Id of the build record
     * @param value The {@link WaveBuildRecord} to be stored
     */
    void createBuildRecord(String buildId, WaveBuildRecord value)

    /**
     * Store the specified build record  both in the short-term cache (redis)
     * and long-term persistence layer (surrealdb)
     *
     * @param buildId The Id of the build record
     * @param value The {@link WaveBuildRecord} to be stored
     */
    void saveBuildRecord(String buildId, WaveBuildRecord value)

    /**
     * Retrieve the build record for the specified id.
     *
     * @param buildId The ID of the build record to be retrieve
     * @return The {@link WaveBuildRecord} associated with the corresponding Id, or {@code null} if it cannot be found
     */
    WaveBuildRecord getBuildRecord(String buildId)
}
