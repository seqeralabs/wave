package io.seqera.wave.api

import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 * Model Fusion verison info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class FusionVersion {

    static final private Pattern VERSION_REGEX = ~/.*\\/v(\d+(?:\.\w+)*)-(\w*)\.json$/

    String number
    String arch

    static FusionVersion from(String uri) {
        if( !uri )
            return null
        final matcher = VERSION_REGEX.matcher(uri)
        if( matcher.matches() ) {
            return new FusionVersion(matcher.group(1), matcher.group(2))
        }
        return null
    }
}
