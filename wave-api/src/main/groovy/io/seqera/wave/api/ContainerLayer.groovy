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

    @Override
    String toString() {
        final loc = toStringLocation0(location)
        return "ContainerLayer[location=${loc}; tarDigest=$tarDigest; gzipDigest=$gzipDigest; gzipSize=$gzipSize]"
    }

    private String toStringLocation0(String location){
        if( !location || !location.startsWith('data:') )
            return location
        return location.length()>25
                ? location.substring(0,25) + '...'
                : location
    }

    /**
     * Copy method
     *
     * @param that The {@link ContainerLayer} to be copied from
     */
    static ContainerLayer copy(ContainerLayer that, boolean stripData=false) {
        if( that==null )
            return null
        def location = that.location
        if( stripData && location && location.startsWith('data:') )
            location = 'data:DATA+OMITTED'
        return new ContainerLayer(location, that.gzipDigest, that.gzipSize, that.tarDigest)
    }
}
