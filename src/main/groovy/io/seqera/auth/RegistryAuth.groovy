package io.seqera.auth

import java.util.regex.Pattern

import groovy.transform.Canonical

/**
 * Model container registry authentication meta-info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class RegistryAuth {

    private static final Pattern AUTH = ~/(?i)(?<type>.+) realm="(?<realm>.+)",service="(?<service>.+)"/

    enum Type { Basic, Bearer }

    final URI realm
    final String service
    final Type type

    boolean isRefreshable() {
        return type==Type.Bearer
    }

    static RegistryAuth parse(String auth) {
        if(!auth)
            return null
        final m1 = AUTH.matcher(auth)
        if( m1.matches() ) {
            final type = Type.valueOf(m1.group('type'))
            return new RegistryAuth(new URI(m1.group('realm')), m1.group('service'), type)
        }
        return null
    }
}
