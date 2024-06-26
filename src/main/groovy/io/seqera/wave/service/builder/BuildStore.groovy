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

    Duration getTimeout()

    Duration getDelay()

    /**
     * Retrieve a container image {@link BuildResult}
     *
     * @param imageName The container image name
     * @return The corresponding {@link BuildResult} or {@code null} otherwise
     */
    BuildResult getBuild(String imageName)

    /**
     * Store a container image build request
     *
     * @param imageName The container image name
     * @param request The {@link BuildResult} object associated to the image name
     */
    void storeBuild(String imageName, BuildResult result)

    /**
     * Store a container image build request using the specified time-to-live duration
     *
     * @param imageName The container image name
     * @param result The {@link BuildResult} object associated to the image name
     * @param ttl The {@link Duration} after which the entry is expired
     */
    void storeBuild(String imageName, BuildResult result, Duration ttl)

    /**
     * Store a build result only if the specified key does not exit
     *
     * @param imageName The container image unique key
     * @param build The {@link BuildResult} desired status to be stored
     * @return {@code true} if the {@link BuildResult} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String imageName, BuildResult build)

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

}
