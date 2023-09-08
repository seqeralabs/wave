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
