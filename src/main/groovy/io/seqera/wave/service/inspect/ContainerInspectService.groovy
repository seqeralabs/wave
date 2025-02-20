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

package io.seqera.wave.service.inspect

import io.micronaut.core.annotation.Nullable
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.model.ContainerOrIndexSpec
import io.seqera.wave.tower.PlatformId
/**
 * Define container inspect service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerInspectService {

    /**
     * Compute Docker config json file that holds container repositories
     * authentication information.
     *
     *
     * @param containerFile
     *      The container definition file of the container to build ie. Dockerfile
     * @param buildRepo
     *      The target repository where the build is pushed
     * @param cacheRepo
     *      The container repository where layers cache is stored
     * @param identity
     *      The platform identity of the user submitting the request
     * @return
     *      A string holding the docker config JSON file for the given repositories
     */
    String credentialsConfigJson(String containerFile, String buildRepo, String cacheRepo, PlatformId identity)

    /**
     * Infer the entrypoint of the container build for the given container file ie. Dockerfile.
     *
     * The entrypoint is determined looking for the ENTRYPOINT statement in the file or fetching it from
     * the base container image defined in the FROM statement.
     *
     * @param containerFile
     *      The container definition file i.e. Dockerfile
     * @param containerPlatform
     *      The container platform identifier i.e. architecture e.g. {@code linux/amd64}, {@code linux/arm64}, etc
     * @param identity
     *      The platform identity of the user submitting the request
     * @return
     *      The container entrypoint model as list of string representing the command to be executed or {@code null}
     *      if not entrypoint is defined
     */
    List<String> containerEntrypoint(String containerFile, ContainerPlatform containerPlatform, PlatformId identity)

    /**
     * Inspect a container image for the given architecture.
     *
     * @param containerImage
     *      The container image to be inspect e.g. ubuntu:latest or docker.io/library/ubuntu:22.04
     * @param arch
     *      The  CPU arch a.k.a. container platform e.g. {@code linux/amd64}, {@code linux/arm64}, etc.
     * @param identity
     *      The platform identity of the user submitting the request
     * @return
     *      The {@link ContainerSpec} object modelling the container image inspect metadata
     */
    ContainerSpec containerSpec(String containerImage, String arch, @Nullable PlatformId identity)

    /**
     * Inspect a container image. If the specified container is a multi-architecture image and not
     * architecture is specified, this method return the container index description, otherwise the
     * container description is returned.
     *
     * @param containerImage
     *      The container image to be inspect e.g. ubuntu:latest or docker.io/library/ubuntu:22.04
     * @param arch
     *      The  CPU arch a.k.a. container platform e.g. {@code linux/amd64}, {@code linux/arm64}, etc.
     * @param identity
     *      The platform identity of the user submitting the request
     * @return
     *      The {@link ContainerOrIndexSpec} object holding either a {@link io.seqera.wave.core.spec.IndexSpec}
     *      or {@link ContainerSpec} object.
     */
    ContainerOrIndexSpec containerOrIndexSpec(String containerImage, @Nullable String arch, @Nullable PlatformId identity)

}
