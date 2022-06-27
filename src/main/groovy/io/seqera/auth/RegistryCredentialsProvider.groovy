package io.seqera.auth

/**
 * Model an abstract provider for container registry credentials
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryCredentialsProvider {

    /**
     * Find the corresponding credentials for the specified registry
     *
     * @param registry
     *      A registry name e.g. docker.io or quay.io
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified registry or {@code null}
     *      if not credentials can be found
     */
    RegistryCredentials getCredentials(String registry)

}
