package io.seqera

import java.nio.file.Path
import java.nio.file.Paths

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class LayerMeta {

    String location
    String gzipDigest
    String tarDigest

    Path getLocationPath() {
        return location ? Paths.get(location) : null
    }
}
