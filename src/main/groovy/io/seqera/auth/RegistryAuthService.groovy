package io.seqera.auth

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
    String getAuthorization(String image, RegistryAuth auth, RegistryCredentials creds)

    /**
     * Invalidate a cached authorization token
     *
     * @param image The image name for which the authorisation is needed
     * @param auth The {@link RegistryAuth} information modelling the target registry
     * @param creds The user credentials
     */
    void invalidateAuthorization(String image, RegistryAuth auth, RegistryCredentials creds)

}
