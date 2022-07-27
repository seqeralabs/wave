package io.seqera.wave.auth

/**
 * Define service to create {@link RegistryCredentials} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryCredentialsFactory {

    RegistryCredentials create(String registry, String userName, String password)

}
