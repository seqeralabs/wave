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

import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model container registry authentication meta-info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class RegistryAuth {

    private static final Pattern AUTH = ~/(?i)(?<type>.+) realm="(?<realm>[^"]+)",service="(?<service>[^"]+)"/
    // some registries doesnt send the service
    private static final Pattern AUTH2 = ~/(?i)(?<type>.+) realm="(?<realm>[^"]+)"/

    enum Type { Basic, Bearer }

    final URI realm
    final String service
    final Type type

    boolean isRefreshable() {
        return type==Type.Bearer
    }

    URI getEndpoint() {
        if( !realm )
            return null
        final uri = realm.toString()
        if( uri?.endsWith('.amazonaws.com/') )
            return new URI(uri + "v2/")
        return new URI(service ? "$uri?service=${service}".toString() : uri)
    }

    static RegistryAuth parse(String auth) {
        if(!auth)
            return null
        final m1 = AUTH.matcher(auth)
        if( m1.find() ) {
            final type = Type.valueOf(m1.group('type'))
            if( m1.group("realm").startsWith("http://") || m1.group("realm").startsWith("https://") ) {
                return new RegistryAuth(new URI(m1.group('realm')), m1.group('service'), type)
            }
        }
        final m2 = AUTH2.matcher(auth)
        if( m2.find() ) {
            final type = Type.valueOf(m2.group('type'))
            if( m2.group("realm").startsWith("http://") || m2.group("realm").startsWith("https://") ) {
                return new RegistryAuth(new URI(m2.group('realm')), null, type)
            }
        }
        return null
    }
}
