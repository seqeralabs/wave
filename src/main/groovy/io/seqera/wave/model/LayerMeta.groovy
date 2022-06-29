package io.seqera.wave.model


import java.nio.file.Path
import java.nio.file.Paths
/**
 * Model meta info properties of layer to be added to the container image
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LayerMeta {

    private Path basePath
    private String location
    private String gzipDigest
    private String tarDigest
    private Integer gzipSize

    String getGzipDigest() { gzipDigest }

    String getTarDigest() { tarDigest }

    Integer getGzipSize() { gzipSize }

    Path getLocationPath() {
        if( !location )
            return null
        final path = Paths.get(location)
        if( path.isAbsolute() || !basePath )
            return path
        return basePath.resolve(path)
    }

    LayerMeta withBase(Path basePath) {
        assert basePath?.isAbsolute()
        this.basePath = basePath
        return this
    }
}
