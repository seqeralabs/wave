package io.seqera.auth

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryCredentialsProvider {

    RegistryCredentials getCredentials(String registry)

}
