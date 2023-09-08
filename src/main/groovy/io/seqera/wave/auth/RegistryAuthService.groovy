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

/**
 * Declares container registry authentication & authorization operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryAuthService {

    /**
     * Perform a registry login
     *
     * @param registry The registry to login against which e.g. {@code docker.io}
     * @param user The registry username
     * @param password The registry password or PAT
     * @return {@code true} if the login was successful or {@code false} otherwise
     */
    boolean login(String registry, String user, String password)

    /**
     * Check if the provided credentials are valid
     *
     * @param registry The registry to check the credentials which e.g. {@code docker.io}
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
