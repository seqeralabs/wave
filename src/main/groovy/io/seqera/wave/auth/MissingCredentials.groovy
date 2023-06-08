package io.seqera.wave.auth


import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * A class that model the absence of registry credentials
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@CompileStatic
class MissingCredentials implements RegistryCredentials {

    final String id

    MissingCredentials(String id) {
        this.id = id
    }

    String getUsername() { null }

    String getPassword() { null }

    @Override
    String toString() {
        return "MissingCredentials[$id]"
    }

    /**
     * @return {@code false} by definition
     */
    boolean asBoolean() {
        return false
    }
}
