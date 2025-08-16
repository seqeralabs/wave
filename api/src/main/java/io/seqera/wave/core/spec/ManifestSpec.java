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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.seqera.wave.model.ContentType;
import static io.seqera.wave.core.spec.Helper.asInteger;
import static io.seqera.wave.core.spec.Helper.fromJson;

/**
 * Model a manifest reference having the following structure
 *
 * <pre>
 *     {
 *      "schemaVersion": 2,
 *      "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
 *      "config": {
 *              "mediaType": "application/vnd.docker.container.image.v1+json",
 *              "size": 1469,
 *              "digest": "sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412"
 *          },
 *      "layers": [
 *          {
 *              "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
 *              "size": 2479,
 *              "digest": "sha256:2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54"
 *          }
 *      ]
 *    }
 * </pre>
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ManifestSpec {

    /**
     * The manifest schema version either 1 or 2
     */
    Integer schemaVersion;

    /**
     * The manifest schema type
     */
    String mediaType;

    /**
     * The object (manifest) reference where the container config is stored.
     * NOTE: This is only available for schema version 2
     */
    ObjectRef config;

    /**
     * The container blob layers reference
     */
    List<ObjectRef> layers;

    /**
     * The container annotations
     * NOTE: This is only available for schema version 2
     */
    Map<String,String> annotations;

    /* REQUIRED BY SERIALIZATION */
    private ManifestSpec() {}

    public ManifestSpec(Integer schema, String mediaType, ObjectRef config, List<ObjectRef> layers, Map<String,String> annotations) {
        this.schemaVersion = schema;
        this.mediaType = mediaType;
        this.config = config;
        this.layers = layers;
        this.annotations = annotations;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public String getMediaType() {
        return mediaType;
    }

    public ObjectRef getConfig() {
        return config;
    }

    public List<ObjectRef> getLayers() {
        return layers;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ManifestSpec that = (ManifestSpec) object;
        return Objects.equals(schemaVersion, that.schemaVersion) && Objects.equals(mediaType, that.mediaType) && Objects.equals(config, that.config) && Objects.equals(layers, that.layers) && Objects.equals(annotations, that.annotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, mediaType, config, layers, annotations);
    }

    @Override
    public String toString() {
        return "ManifestSpec{" +
                "schemaVersion=" + schemaVersion +
                ", mediaType='" + mediaType + '\'' +
                ", config=" + config +
                ", layers=" + layers +
                ", annotations=" + annotations +
                '}';
    }

    static public ManifestSpec of(String json) {
        return of(fromJson(json,Map.class));
    }

    static public ManifestSpec of(Map<String,?> object) {
        return new ManifestSpec(
                asInteger(object.get("schemaVersion")),
                (String) object.get("mediaType"),
                ObjectRef.of(object.containsKey("config") ? (Map) object.get("config") : Map.of()),
                ObjectRef.of((List<Map>) object.get("layers")),
                object.containsKey("annotations") ? (Map<String,String>) object.get("annotations") : Map.of()
        );
    }

    static public ManifestSpec parseV1(Map<String,Object> opts) {
        // fetch layers
        List<Map> fsLayers = opts.containsKey("fsLayers") ? (List<Map>) opts.get("fsLayers") : List.of();
        final List<ObjectRef> layers = fsLayers
                .stream()
                .map(it-> ObjectRef.of(Map.of("digest", it.get("blobSum"))))
                .collect(Collectors.toList());
        // reverse the order of the layers
        // because in manifest v1 are reported from the last to the first
        Collections.reverse(layers);

        return new ManifestSpec(
                1,
                ContentType.DOCKER_MANIFEST_V1_JWS_TYPE,
                null,
                layers,
                Map.of() );
    }

    static public ManifestSpec parseV1(String json) {
        return parseV1(fromJson(json,Map.class));
    }

}
