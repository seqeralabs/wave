package io.seqera.auth

import groovy.transform.Canonical

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class BasicRegistryCredentials implements RegistryCredentials {
    final String username
    final String password
}
