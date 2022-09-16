package io.seqera.wave.util

import groovy.transform.CompileStatic

/**
 * Implements Json views marker classes to be used with @JsonView annotation
 *
 * See https://www.baeldung.com/jackson-json-view-annotation
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class Views {

    /** Marker class for public fields */
    static abstract class Public { }
}
