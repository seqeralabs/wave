package io.seqera.wave.api;

import java.util.Objects;

import static io.seqera.wave.api.ObjectUtils.isEmpty;

/**
 * Model a container layer meta-info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerLayer {

    public String location;
    public String gzipDigest;
    public Integer gzipSize;
    public String tarDigest;

    public ContainerLayer() {}

    public ContainerLayer(String location) {
        this.location = location;
    }

    public ContainerLayer(String location, String gzipDigest) {
        this.location = location;
        this.gzipDigest = gzipDigest;
    }

    public ContainerLayer(String location, String gzipDigest, Integer gzipSize) {
        this.location = location;
        this.gzipDigest = gzipDigest;
        this.gzipSize = gzipSize;
    }

    public ContainerLayer(String location, String gzipDigest, Integer gzipSize, String tarDigest) {
        this.location = location;
        this.gzipDigest = gzipDigest;
        this.gzipSize = gzipSize;
        this.tarDigest = tarDigest;
    }

    public void validate() {
        if( isEmpty(location) ) throw new IllegalArgumentException("Missing layer location");
        if( isEmpty(gzipDigest) ) throw new IllegalArgumentException("Missing layer gzip digest");
        if( isEmpty(gzipSize) ) throw new IllegalArgumentException("Missing layer gzip size");
        if( isEmpty(tarDigest) ) throw new IllegalArgumentException("Missing layer tar digest");

        if( !gzipDigest.startsWith("sha256:") )
            throw new IllegalArgumentException("Missing layer gzip digest should start with the 'sha256:' prefix -- offending value: " + gzipDigest);
        if( !tarDigest.startsWith("sha256:") )
            throw new IllegalArgumentException("Missing layer tar digest should start with the 'sha256:' prefix -- offending value: " + tarDigest);
    }

    @Override
    public String toString() {
        final String loc = toStringLocation0(location);
        return String.format("ContainerLayer[location=%s; tarDigest=%s; gzipDigest=%s; gzipSize=%s]", loc, tarDigest, gzipDigest, gzipSize);
    }

    private String toStringLocation0(String location){
        if( location==null || !location.startsWith("data:") )
            return location;
        return location.length()>25
                ? location.substring(0,25) + "..."
                : location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerLayer that = (ContainerLayer) o;
        return Objects.equals(location, that.location) && Objects.equals(gzipDigest, that.gzipDigest) && Objects.equals(gzipSize, that.gzipSize) && Objects.equals(tarDigest, that.tarDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, gzipDigest, gzipSize, tarDigest);
    }

    /**
     * Copy method
     *
     * @param that The {@link ContainerLayer} to be copied from
     */
    static ContainerLayer copy(ContainerLayer that) {
        return copy(that, false);
    }

    static ContainerLayer copy(ContainerLayer that, boolean stripData) {
        if( that==null )
            return null;
        String location = that.location;
        if( stripData && !isEmpty(location) && location.startsWith("data:") )
            location = "data:DATA+OMITTED";
        return new ContainerLayer(location, that.gzipDigest, that.gzipSize, that.tarDigest);
    }
}
