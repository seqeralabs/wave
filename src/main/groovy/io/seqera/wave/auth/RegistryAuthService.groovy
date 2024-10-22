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

package io.seqera.wave.auth

import io.seqera.wave.exception.RegistryUnauthorizedAccessException

/**
 * Declares container registry authentication & authorization operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryAuthService {

    /**
     * Perform a registry login
     *
     * @param registry
     *      The registry to login against which e.g. {@code docker.io} or a container
     *      repository e.g. {@code docker.io/library/ubuntu}
     * @param user The registry username
     * @param password The registry password or PAT
     * @return {@code true} if the login was successful or {@code false} otherwise
     */
    boolean login(String registry, String user, String password)

    /**
     * Check if the provided credentials are valid
     *
     * @param registry
     *      The registry to check the credentials which e.g. {@code docker.io} or a container
     *      repository e.g. {@code docker.io/library/ubuntu}
     * @param user The registry username
     * @param password The registry password or PAT
     * @return {@code true} if the login was successful or {@code false} otherwise
     */
    boolean validateUser(String registry, String user, String password)

    /**
     * Get the authorization header for the given image, registry and credentials.
     * This can be either a bearer token header or a basic auth header.
     *
     * @param image The image name for which the authorisation is needed
     * @param auth The {@link RegistryAuth} information modelling the target registry
     * @param creds The user credentials
     * @return The authorization header including the 'Basic' or 'Bearer' prefix
     */
    String getAuthorization(String image, RegistryAuth auth, RegistryCredentials creds) throws RegistryUnauthorizedAccessException

    /**
     * Invalidate a cached authorization token
     *
     * @param image The image name for which the authorisation is needed
     * @param auth The {@link RegistryAuth} information modelling the target registry
     * @param creds The user credentials
     */
    void invalidateAuthorization(String image, RegistryAuth auth, RegistryCredentials creds)

}
