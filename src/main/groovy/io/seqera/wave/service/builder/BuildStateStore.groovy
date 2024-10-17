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

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import io.seqera.wave.store.state.CountResult

/**
 * Define build request store operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
interface BuildStateStore {

    /**
     * Retrieve the build entry {@link BuildEntry} for a given container image name
     *
     * @param imageName
     *      The container image name
     * @return
     *      The corresponding {@link BuildEntry} or {@code null} otherwise
     */
    BuildEntry getBuild(String imageName)

    /**
     * Retrieve the build entry {@link BuildResult} for a given container image name
     *
     * @param imageName
     *      The container image name
     * @return
     *      The corresponding {@link BuildEntry} or {@code null} otherwise
     */
    BuildResult getBuildResult(String imageName)

    /**
     * Store a container image build request
     *
     * @param imageName The container image name
     * @param request The {@link BuildEntry} object associated to the image name
     */
    void storeBuild(String imageName, BuildEntry result)

    /**
     * Store a container image build request using the specified time-to-live duration
     *
     * @param imageName The container image name
     * @param result The {@link BuildEntry} object associated to the image name
     * @param ttl The {@link Duration} after which the entry is expired
     */
    @Deprecated
    void storeBuild(String imageName, BuildEntry result, Duration ttl)

    /**
     * Store a {@link BuildEntry} object only if the specified key does not exit
     *
     * @param imageName The container image unique key
     * @param build The {@link BuildEntry} desired status to be stored
     * @return {@code true} if the {@link BuildEntry} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String imageName, BuildEntry build)

    /**
     * Store a {@link BuildEntry} object only if the specified key does not exit.
     * When the entry is stored the {@code buildId} fields in the request and response
     * and incremented by 1.
     *
     * @param imageName
     *      The container image name used as store key.
     * @param build
     *      A {@link BuildEntry} object to be stored
     * @return
     *      A {@link CountResult} object holding the {@link BuildEntry} object with the updated {@code buildId}
     *      if the store is successful, or the currently store {@link BuildEntry} if the key already exist.
     */
    CountResult<BuildEntry> putIfAbsentAndCount(String imageName, BuildEntry build)

    /**
     * Remove the build status for the given image name
     *
     * @param imageName
     */
    void removeBuild(String imageName)

    /**
     * Await for the container image build completion
     *
     * @param imageName
     *      The target container image name to be build
     * @return
     *      the {@link CompletableFuture} holding the {@link BuildResult} associated with
     *      specified image name or {@code null} if no build is associated for the
     *      given image name
     */
    CompletableFuture<BuildResult> awaitBuild(String imageName)

    /**
     * Load a build entry via the record id
     *
     * @param recordId The ID of the record to be loaded
     * @return The {@link BuildEntry} with with corresponding Id of {@code null} if it cannot be found
     */
    BuildEntry findByRequestId(String recordId)
}
