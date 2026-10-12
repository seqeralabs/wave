/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.api;

import java.util.Objects;

import static io.seqera.wave.api.ObjectUtils.isEmpty;

/**
 * Model a container layer meta-info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerLayer {

    /**
     * the layer location, it can be either `http:` or `https:` prefixed URI
     * or a `data:` pseudo-protocol followed by a base64 encoded tar gzipped layer payload
     */
    public String location;

    /**
     * The layer gzip sha256 checksum
     */
    public String gzipDigest;

    /**
     * The layer gzip size in bytes
     */
    public Integer gzipSize;

    /**
     * The layer tar sha256 checksum
     */
    public String tarDigest;

    /**
     * When {@code this layer is not added in the final config fingerprint}
     */
    public Boolean skipHashing;

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
