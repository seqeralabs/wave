/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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

    default RegistryCredentials getDefaultCredentials(ContainerPath container) {
        return getDefaultCredentials((String)(container?.registry))
    }

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
