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

package io.seqera.wave.core.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.seqera.wave.core.spec.Helper.asLong;
import static io.seqera.wave.core.spec.Helper.fromJson;

/**
 * Model an container OCI index specification
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class IndexSpec {

    /**
     * The index schema version
     */
    Integer schemaVersion;

    /**
     * The index media type e.g. {@code application/vnd.oci.image.index.v1+json}
     */
    String mediaType;

    /**
     * A list of one or more {@link ManifestSpec} that compose the index.
     */
    List<ManifestSpec> manifests;

    /**
     * The digest checksum associated with the container
     */
    String digest;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexSpec indexSpec = (IndexSpec) o;
        return Objects.equals(mediaType, indexSpec.mediaType)
                && Objects.equals(schemaVersion, indexSpec.schemaVersion)
                && Objects.equals(manifests, indexSpec.manifests)
                && Objects.equals(digest, indexSpec.digest)
                ;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public String getMediaType() {
        return mediaType;
    }

    public List<ManifestSpec> getManifests() {
        return manifests;
    }

    public String getDigest() { return digest; }

    public IndexSpec withDigest(String digest) {
        this.digest = digest;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaType, schemaVersion, manifests);
    }

    @Override
    public String toString() {
        return "IndexSpec{" +
                "schemaVersion=" + schemaVersion +
                ", mediaType='" + mediaType + '\'' +
                ", manifests=" + manifests +
                ", digest='" + digest + '\'' +
                '}';
    }

    /**
     * Parse an Index JSON document string into a {@link IndexSpec} object.
     *
     * @param json
     *      A Index JSON document as a string value.
     * @return
     *      An instance of {@link IndexSpec}
     */
    static public IndexSpec parse(String json) {
        return fromJson(json, IndexSpec.class);
    }

    /**
     * Model a container object reference i.e. manifest or blob
     *
     * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
     */
    static public class ManifestSpec {

        String mediaType;
        String digest;
        Long size;
        Map<String,String> annotations;
        PlatformSpec platform;

        /* REQUIRED BY SERIALIZATION */
        private ManifestSpec() {}

        public ManifestSpec(String mediaType, String digest, Long size, Map<String,String> annotations, PlatformSpec platform) {
            this.mediaType = mediaType;
            this.digest = digest;
            this.size = size;
            this.annotations = annotations;
            this.platform = platform;
        }

        public ManifestSpec(ManifestSpec that) {
            this.mediaType = that.mediaType;
            this.digest = that.digest;
            this.size = that.size;
            this.annotations = that.annotations;
            this.platform = that.platform;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getDigest() {
            return digest;
        }

        public Long getSize() {
            return size;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public PlatformSpec getPlatform() {
            return platform;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            ManifestSpec objectRef = (ManifestSpec) object;
            return Objects.equals(mediaType, objectRef.mediaType)
                    && Objects.equals(digest, objectRef.digest)
                    && Objects.equals(size, objectRef.size)
                    && Objects.equals(annotations, objectRef.annotations)
                    && Objects.equals(platform, objectRef.platform);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mediaType, digest, size, annotations, platform);
        }

        @Override
        public String toString() {
            return "ManifestRef{" +
                    "mediaType='" + mediaType + '\'' +
                    ", digest='" + digest + '\'' +
                    ", size=" + size +
                    ", annotations=" + annotations +
                    ", platform=" + platform +
                    '}';
        }

        static public List<ManifestSpec> of(List<Map> values) {
            return values!=null && !values.isEmpty()
                    ? values.stream().map(ManifestSpec::of).collect(Collectors.toList())
                    : List.<ManifestSpec>of();
        }

        static public ManifestSpec of(Map<String,?> object) {
            return new ManifestSpec(
                    (String) object.get("mediaType"),
                    (String) object.get("digest"),
                    asLong(object.get("size")),
                    (Map<String,String>) object.get("annotations"),
                    object.containsKey("platform") ? PlatformSpec.of((Map) object.get("platform")) : null
            );
        }
    }

    /**
     * Model a container platform spec
     *
     * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
     */
    static public class PlatformSpec {

        String architecture;
        String os;
        String variant;

        PlatformSpec() { }

        public PlatformSpec(String architecture, String os) {
            this.architecture = architecture;
            this.os = os;
        }

        public PlatformSpec(String architecture, String os, String variant) {
            this.architecture = architecture;
            this.os = os;
            this.variant = variant;
        }

        public String getArchitecture() {
            return architecture;
        }

        public String getOs() {
            return os;
        }

        public String getVariant() {
            return variant;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlatformSpec that = (PlatformSpec) o;
            return Objects.equals(architecture, that.architecture)
                    && Objects.equals(os, that.os)
                    && Objects.equals(variant, that.variant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(architecture, os, variant);
        }

        @Override
        public String toString() {
            return "PlatformSpec{" +
                    "architecture='" + architecture + '\'' +
                    ", os='" + os + '\'' +
                    ", variant='" + variant + '\'' +
                    '}';
        }

        static PlatformSpec of(Map object) {
            return new PlatformSpec(
                    (String) object.get("architecture"),
                    (String) object.get("os"),
                    (String) object.get("variant")
            );
        }
    }
}
