package io.seqera.model

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LayerMeta {

    String location
    String gzipDigest
    String tarDigest

    Path getLocationPath() {
        return location
                ? Paths.get(location)
                : null
    }

    int getGzipSize() {
        return locationPath
                ? Files.size(locationPath) as int
                : 0
    }
}
