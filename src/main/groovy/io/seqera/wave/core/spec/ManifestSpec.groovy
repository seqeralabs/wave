package io.seqera.wave.core.spec

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.model.ContentType

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
@Canonical
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ManifestSpec {

    /**
     * The manifest schema version either 1 or 2
     */
    Integer schemaVersion

    /**
     * The manifest schema type
     */
    String mediaType

    /**
     * The object (manifest) reference where the container config is stored.
     * NOTE: This is only available for schema version 2
     */
    ObjectRef config

    /**
     * The container blob layers reference
     */
    List<ObjectRef> layers

    /**
     * The container annotations
     * NOTE: This is only available for schema version 2
     */
    Map<String,String> annotations

    static ManifestSpec of(String json) {
        return of(new JsonSlurper().parseText(json) as Map)
    }

    static ManifestSpec of(Map<String,?> object) {
        return new ManifestSpec(
                object.get('schemaVersion') as Integer,
                object.get('mediaType') as String,
                ObjectRef.of(object.get('config') as Map ?: Map.of()),
                ObjectRef.of(object.get('layers') as List<Map>),
                object.get('annotations') as Map ?: Map.of()
        )
    }

    static ManifestSpec parseV1(Map<String,Object> opts) {
        // fetch layers
        final layers = (opts.fsLayers as List<Map>)
                ?.reverse()
                ?.collect(it-> ObjectRef.of(Map.of('digest', it.blobSum)))

        return new ManifestSpec(
                1,
                ContentType.DOCKER_MANIFEST_V1_JWS_TYPE,
                null,
                layers,
                Map.of() )
    }

    static ManifestSpec parseV1(String json) {
        return parseV1(new JsonSlurper().parseText(json) as Map)
    }
}
