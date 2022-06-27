package io.seqera.auth

import groovy.transform.Canonical

/**
 * Simple registry credentials made up of static username and password
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class SimpleRegistryCredentials implements RegistryCredentials {
    final String username
    final String password
}
