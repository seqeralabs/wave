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

package io.seqera.wave.service

import io.seqera.wave.tower.PlatformId

/**
 * Declare operations to access container registry credentials from Tower
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CredentialsService {

    /**
     * Find the container registry credentials for the given repository
     *
     * @param repository
     *      The target repository e.g. {@code docker.io/library/ubuntu}, or a bare registry name e.g. {@code docker.io}
     * @param identity
     *      The platform identity of the user submitting the request
     * @return
     *      The matching {@link ContainerRegistryKeys} or {@code null} when no credentials match
     */
    ContainerRegistryKeys findRegistryCreds(String repository, PlatformId identity)

}
