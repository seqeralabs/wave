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

package io.seqera.wave.core

/**
 * Define container name components
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerPath {

    /**
     * The image name in a container path. For example given then container:
     * {@code docker.io/library/busybox:latest} returns {@code library/busybox}
     *
     * @return The container image name
     */
    String getImage()

    /**
     * The container registry server name. For example given then container:
     * {@code docker.io/library/busybox:latest} returns {@code docker.io}
     *
     * @return The container registry server name
     */
    String getRegistry()

    /**
     * The container repository name defined as the registry name followed by the image name.
     * For example given the container {@code docker.io/library/busybox:latest} returns {@code docker.io/library/busybox}
     *
     * @return The container repository name
     */
    String getRepository()

    /**
     * The container reference name a.k.a tag. For example given the container
     * {@code docker.io/library/busybox:latest} returns {@code latest}
     *
     * @return The container reference
     */
    String getReference()

    /**
     * The fully qualified container name e.g. {@code docker.io/library/busybox:latest}
     * @return The container name
     */
    String getTargetContainer()
}
