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
/**
 * Define build request store operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
interface BuildStore {

    /**
     * Retrieve the build entry {@link BuildStoreEntry} for a given container image name
     *
     * @param imageName
     *      The container image name
     * @return
     *      The corresponding {@link BuildStoreEntry} or {@code null} otherwise
     */
    BuildStoreEntry getBuild(String imageName)

    /**
     * Retrieve the build entry {@link BuildResult} for a given container image name
     *
     * @param imageName
     *      The container image name
     * @return
     *      The corresponding {@link BuildStoreEntry} or {@code null} otherwise
     */
    BuildResult getBuildResult(String imageName)

    /**
     * Store a container image build request
     *
     * @param imageName The container image name
     * @param request The {@link BuildStoreEntry} object associated to the image name
     */
    void storeBuild(String imageName, BuildStoreEntry result)

    /**
     * Store a container image build request using the specified time-to-live duration
     *
     * @param imageName The container image name
     * @param result The {@link BuildStoreEntry} object associated to the image name
     * @param ttl The {@link Duration} after which the entry is expired
     */
    void storeBuild(String imageName, BuildStoreEntry result, Duration ttl)

    /**
     * Store a build result only if the specified key does not exit
     *
     * @param imageName The container image unique key
     * @param build The {@link BuildStoreEntry} desired status to be stored
     * @return {@code true} if the {@link BuildStoreEntry} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String imageName, BuildStoreEntry build)

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
     * @return The {@link BuildStoreEntry} with with corresponding Id of {@code null} if it cannot be found
     */
    BuildStoreEntry getByRecordId(String recordId)
}
