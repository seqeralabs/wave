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

package io.seqera.wave.auth


import io.seqera.wave.core.ContainerPath

/**
 * Model an abstract provider for container registry credentials
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryCredentialsProvider {

    /**
     * Provides the credentials for the specified registry.
     *
     * @param registry
     *      A registry name e.g. docker.io or quay.io. When empty {@code docker.io} is assumed.
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified registry or {@code null}
     *      if not credentials can be found
     */
    RegistryCredentials getDefaultCredentials(String registry)

    RegistryCredentials getDefaultCredentials(ContainerPath container)

    /**
     * Provides the credentials for the specified container associated with the user and tower
     * workspace specified.
     *
     * @param container
     *      A container name e.g. docker.io/library/ubuntu.
     * @param userId
     *      The tower user Id.
     * @param workspaceId
     *      The tower workspace Id.
     * @param towerToken
     *      The auth token used to access tower
     * @param towerEndpoint
     *      The tower endpoint used in the registration
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified container or {@code null}
     *      if not credentials can be found
     */
    RegistryCredentials getUserCredentials(ContainerPath container, Long userId, Long workspaceId, String towerToken, String towerEndpoint)

}
