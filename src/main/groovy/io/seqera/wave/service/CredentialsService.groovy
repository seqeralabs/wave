/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import io.seqera.wave.core.ContainerPath

/**
 * Declare operations to access container registry credentials from Tower
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CredentialsService {

    /**
     *
     * @param container
     *          The container for which the registry credentials should be retrieved
     * @param userId
     *          The unique ID of the Tower user
     * @param workspaceId
     *          The unique ID of the Tower workspace
     * @param towerToken
     *          The Tower access token
     * @param towerEndpoint
     *          The Tower endpoint
     * @return
     *          The container registry credentials to be used to authenticate the specified container registry or repository
     *          or {@code null} if no match is found
     */
    ContainerRegistryKeys findRegistryCreds(ContainerPath container, Long userId, Long workspaceId, String towerToken, String towerEndpoint)

}
