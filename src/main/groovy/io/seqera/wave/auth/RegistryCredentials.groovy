package io.seqera.wave.auth

/**
 * Simple container registry credentials interface
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryCredentials {

    String getUsername()
    String getPassword()

}
