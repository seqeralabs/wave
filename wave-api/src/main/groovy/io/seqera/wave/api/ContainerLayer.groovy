package io.seqera.wave.api


import groovy.transform.Canonical
import groovy.transform.CompileStatic
/**
 * Model a container layer meta-info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerLayer {
    String location
    String gzipDigest
    Integer gzipSize
    String tarDigest

    void validate() {
        if( !location ) throw new IllegalArgumentException("Missing layer location")
        if( !gzipDigest ) throw new IllegalArgumentException("Missing layer gzip digest")
        if( !gzipSize ) throw new IllegalArgumentException("Missing layer gzip size")
        if( !tarDigest ) throw new IllegalArgumentException("Missing layer tar digest")

        if( !gzipDigest.startsWith('sha256:') )
            throw new IllegalArgumentException("Missing layer gzip digest should start with the 'sha256:' prefix -- offending value: $gzipDigest")
        if( !tarDigest.startsWith('sha256:') )
            throw new IllegalArgumentException("Missing layer tar digest should start with the 'sha256:' prefix -- offending value: $tarDigest")
    }

}
