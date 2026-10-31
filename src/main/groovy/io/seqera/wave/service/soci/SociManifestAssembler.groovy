/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.soci

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.seqera.wave.util.RegHelper
import static io.seqera.wave.model.ContentType.OCI_IMAGE_CONFIG_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
/**
 * Create the documents of a SOCI-enabled image (SOCI index manifest v2), the same way
 * as the {@code soci convert} command:
 * <ul>
 *     <li>the SOCI index manifest, listing the zTOC of each indexed layer;</li>
 *     <li>the image manifest, annotated with the SOCI index digest and converted to OCI media types;</li>
 *     <li>the image index, pointing to the annotated manifest and with the SOCI index descriptor appended.</li>
 * </ul>
 *
 * Documents are serialized with the same field order as soci (Go structs, sorted annotations)
 * so that the same input gives the same digests.
 *
 * See https://github.com/awslabs/soci-snapshotter/blob/main/docs/soci-index-manifest-v2.md
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SociManifestAssembler {

    static final public String SOCI_INDEX_ARTIFACT_TYPE = 'application/vnd.amazon.soci.index.v2+json'
    static final public String ZTOC_MEDIA_TYPE = 'application/octet-stream'
    static final public String ANNOTATION_INDEX_DIGEST = 'com.amazon.soci.index-digest'
    static final public String ANNOTATION_IMAGE_MANIFEST_DIGEST = 'com.amazon.soci.image-manifest-digest'
    static final public String ANNOTATION_LAYER_DIGEST = 'com.amazon.soci.image-layer-digest'
    static final public String ANNOTATION_LAYER_MEDIA_TYPE = 'com.amazon.soci.image-layer-mediaType'
    static final public String ANNOTATION_SPAN_SIZE = 'com.amazon.soci.span-size'
    static final public String ANNOTATION_BUILD_TOOL = 'com.amazon.soci.build-tool-identifier'
    static final public String BUILD_TOOL = 'Seqera Wave'
    static final public String EMPTY_JSON = '{}'
    static final public String EMPTY_JSON_DIGEST = 'sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a'

    static private final List<String> DESCRIPTOR_FIELDS = ['mediaType','digest','size','urls','annotations','data','platform','artifactType']
    static private final List<String> PLATFORM_FIELDS = ['architecture','os','os.version','os.features','variant']

    /**
     * Find the layers of an image manifest that must be indexed: gzip layers not smaller than {@code minLayerSize}
     *
     * @param manifestJson The image manifest
     * @param minLayerSize The minimum size of an indexed layer
     * @return The layer descriptors to be indexed, in manifest order
     */
    static List<Map> eligibleLayers(String manifestJson, long minLayerSize) {
        final manifest = parse(manifestJson)
        final layers = (manifest.layers ?: []) as List<Map>
        return layers.findAll((Map it) -> isGzip(it.mediaType as String) && (it.size as long) >= minLayerSize)
    }

    static boolean isGzip(String mediaType) {
        return mediaType?.endsWith('.tar+gzip') || mediaType?.endsWith('.tar.gzip')
    }

    /**
     * @return {@code true} when the image index already has a SOCI index manifest
     */
    static boolean hasSociIndex(String indexJson) {
        final manifests = (parse(indexJson).manifests ?: []) as List<Map>
        return manifests.any((Map it) -> it.artifactType == SOCI_INDEX_ARTIFACT_TYPE)
    }

    static SociDocs assemble(String manifestJson, Map<String,ZtocEntry> ztocs, long spanSize) {
        return assemble(manifestJson, ztocs, spanSize, BUILD_TOOL)
    }

    /**
     * Create the SOCI index manifest and the annotated image manifest
     *
     * @param manifestJson The image manifest
     * @param ztocs The zTOCs available, by layer digest
     * @param spanSize The zTOC span size
     * @param buildTool The build tool identifier annotated on the SOCI index manifest
     * @return The SOCI documents, or {@code null} when no layer of the manifest has a zTOC
     */
    static SociDocs assemble(String manifestJson, Map<String,ZtocEntry> ztocs, long spanSize, String buildTool) {
        final manifest = parse(manifestJson)
        final layers = (manifest.layers ?: []) as List<Map>
        final indexed = layers
                .collect((Map it) -> ztocs.get(it.digest as String))
                .findAll((ZtocEntry it) -> it != null)
        if( !indexed )
            return null

        final sociIndex = sociIndexManifest(indexed, spanSize, buildTool)
        final sociIndexDigest = RegHelper.digest(sociIndex)
        final annotated = annotatedManifest(manifest, sociIndexDigest)
        return new SociDocs(
                annotated,
                RegHelper.digest(annotated),
                annotated.bytes.length,
                sociIndex,
                sociIndexDigest,
                sociIndex.bytes.length,
                indexed )
    }

    static protected String sociIndexManifest(List<ZtocEntry> ztocs, long spanSize, String buildTool) {
        final layers = ztocs.collect((ZtocEntry it) -> ztocDescriptor(it, spanSize))
        final result = new LinkedHashMap<String,Object>()
        result.schemaVersion = 2
        result.mediaType = OCI_IMAGE_MANIFEST_V1
        result.config = descriptor(SOCI_INDEX_ARTIFACT_TYPE, EMPTY_JSON_DIGEST, EMPTY_JSON.length())
        result.layers = layers
        result.annotations = [(ANNOTATION_BUILD_TOOL): buildTool]
        return JsonOutput.toJson(result)
    }

    static protected Map ztocDescriptor(ZtocEntry ztoc, long spanSize) {
        final result = descriptor(ZTOC_MEDIA_TYPE, ztoc.ztocDigest, ztoc.ztocSize)
        result.annotations = sorted([
                (ANNOTATION_LAYER_DIGEST): ztoc.layerDigest,
                (ANNOTATION_LAYER_MEDIA_TYPE): ztoc.layerMediaType,
                (ANNOTATION_SPAN_SIZE): String.valueOf(spanSize) ])
        return result
    }

    static protected String annotatedManifest(Map manifest, String sociIndexDigest) {
        final annotations = new LinkedHashMap<String,Object>((manifest.annotations ?: [:]) as Map)
        annotations.put(ANNOTATION_INDEX_DIGEST, sociIndexDigest)
        // like soci, convert the manifest and the config to OCI media types:
        // registries may reject an OCI index mixing Docker manifests
        final config = new LinkedHashMap<String,Object>(manifest.config as Map)
        config.mediaType = OCI_IMAGE_CONFIG_V1
        final result = new LinkedHashMap<String,Object>()
        result.schemaVersion = manifest.schemaVersion
        result.mediaType = OCI_IMAGE_MANIFEST_V1
        if( manifest.artifactType )
            result.artifactType = manifest.artifactType
        result.config = canonical(config)
        result.layers = (manifest.layers as List<Map>).collect(SociManifestAssembler.&canonical)
        if( manifest.subject )
            result.subject = canonical(manifest.subject as Map)
        result.annotations = sorted(annotations)
        return JsonOutput.toJson(result)
    }

    /**
     * Update an image index so that each indexed entry points to its annotated manifest and
     * the corresponding SOCI index descriptor is appended. Other entries are left untouched
     *
     * @param indexJson The image index
     * @param docs The SOCI documents, by digest of the original image manifest
     * @return The updated image index
     */
    static String updateIndex(String indexJson, Map<String,SociDocs> docs) {
        final index = parse(indexJson)
        final manifests = index.manifests as List<Map>
        final appended = new ArrayList<Map>()
        for( int i=0; i<manifests.size(); i++ ) {
            final it = manifests.get(i)
            final entry = docs.get(it.digest as String)
            if( !entry )
                continue
            manifests.set(i, imageDescriptor(entry, it.platform as Map))
            appended.add(sociDescriptor(entry, it.platform as Map))
        }
        manifests.addAll(appended)
        index.mediaType = OCI_IMAGE_INDEX_V1
        return JsonOutput.toJson(index)
    }

    /**
     * Create an image index for a single SOCI-enabled image manifest
     *
     * @param docs The SOCI documents
     * @param platform The image platform e.g. {@code [architecture:'amd64', os:'linux']}
     * @return The image index
     */
    static String wrapInIndex(SociDocs docs, Map platform) {
        final result = new LinkedHashMap<String,Object>()
        result.schemaVersion = 2
        result.mediaType = OCI_IMAGE_INDEX_V1
        result.manifests = [imageDescriptor(docs, platform), sociDescriptor(docs, platform)]
        return JsonOutput.toJson(result)
    }

    static protected Map imageDescriptor(SociDocs docs, Map platform) {
        final result = descriptor(OCI_IMAGE_MANIFEST_V1, docs.manifestDigest, docs.manifestSize)
        // like soci, the descriptor carries the manifest annotations
        result.annotations = parse(docs.manifestJson).annotations
        if( platform )
            result.platform = canonicalPlatform(platform)
        return result
    }

    static protected Map sociDescriptor(SociDocs docs, Map platform) {
        final result = descriptor(OCI_IMAGE_MANIFEST_V1, docs.sociIndexDigest, docs.sociIndexSize)
        result.annotations = [(ANNOTATION_IMAGE_MANIFEST_DIGEST): docs.manifestDigest]
        if( platform )
            result.platform = canonicalPlatform(platform)
        result.artifactType = SOCI_INDEX_ARTIFACT_TYPE
        return result
    }

    static protected Map<String,Object> descriptor(String mediaType, String digest, long size) {
        final result = new LinkedHashMap<String,Object>()
        result.mediaType = mediaType
        result.digest = digest
        result.size = size
        return result
    }

    /**
     * Order the fields of a descriptor like the OCI Go structs, annotations sorted
     */
    static protected Map canonical(Map descriptor) {
        final result = new LinkedHashMap<String,Object>()
        for( String key : DESCRIPTOR_FIELDS ) {
            final value = descriptor.get(key)
            if( value == null )
                continue
            if( key == 'annotations' )
                result.put(key, sorted(value as Map))
            else if( key == 'platform' )
                result.put(key, canonicalPlatform(value as Map))
            else
                result.put(key, value)
        }
        return result
    }

    static protected Map canonicalPlatform(Map platform) {
        final result = new LinkedHashMap<String,Object>()
        for( String key : PLATFORM_FIELDS ) {
            if( platform.get(key) != null )
                result.put(key, platform.get(key))
        }
        return result
    }

    static protected Map sorted(Map map) {
        return new TreeMap(map)
    }

    static protected Map parse(String json) {
        return new JsonSlurper().parseText(json) as Map
    }
}
