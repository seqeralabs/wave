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

import java.nio.file.Path
import java.util.function.Function

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.util.RegHelper
import static io.seqera.wave.model.ContentType.DOCKER_IMAGE_INDEX_V2
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V2_TYPE
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
/**
 * Helpers shared by the SOCI services to select the image manifests to index and resolve their platform
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SociPlatforms {

    /**
     * @return The repository of an image including the registry e.g. {@code docker.io/library/ubuntu}
     */
    static String repository(String image) {
        final coords = ContainerCoordinates.parse(image)
        return "${coords.registry}/${coords.image}".toString()
    }

    static boolean isIndex(Map json) {
        return json.mediaType==OCI_IMAGE_INDEX_V1 || json.mediaType==DOCKER_IMAGE_INDEX_V2
    }

    /**
     * The image manifests of a container matching the target platform
     *
     * @param content The container manifest, an image index or an image manifest
     * @param platform The target platform
     * @param fetch Fetch an image manifest by digest, returning {@code null} when missing
     * @return The image manifests, by digest
     */
    static Map<String,String> platformManifests(String content, ContainerPlatform platform, Function<String,String> fetch) {
        final json = new JsonSlurper().parseText(content) as Map
        if( !isIndex(json) )
            return Map.of(RegHelper.digest(content), content)
        final result = new LinkedHashMap<String,String>()
        for( Map entry : targetEntries(json, platform) ) {
            final manifest = fetch.apply(entry.digest as String)
            if( manifest )
                result.put(entry.digest as String, manifest)
        }
        return result
    }

    /**
     * Select the image manifest entries of an image index matching the target platform, skipping
     * non-image manifests e.g. attestations and SOCI indexes
     *
     * @param index The parsed image index
     * @param platform The target platform
     * @return The matching manifest descriptors
     */
    static List<Map> targetEntries(Map index, ContainerPlatform platform) {
        final manifests = (index.manifests ?: []) as List<Map>
        return manifests.findAll((Map it) -> isTarget(it, platform))
    }

    static boolean isTarget(Map entry, ContainerPlatform platform) {
        final media = entry.mediaType as String
        if( media!=OCI_IMAGE_MANIFEST_V1 && media!=DOCKER_MANIFEST_V2_TYPE )
            return false
        if( entry.artifactType )
            return false
        final entryPlatform = entry.platform as Map
        return entryPlatform && platform.platforms.any((ContainerPlatform.Platform it) -> it.matches(entryPlatform))
    }

    /**
     * The platform descriptor of an image, from its config blob, falling back to the target platform
     *
     * @param configJson The image config blob, or {@code null} when it cannot be read
     * @param platform The target platform
     * @return The platform descriptor e.g. {@code [architecture:'arm64', os:'linux', variant:'v8']}
     */
    static Map platformOf(String configJson, ContainerPlatform platform) {
        final json = configJson ? new JsonSlurper().parseText(configJson) as Map : [:]
        final result = new LinkedHashMap<String,Object>()
        result.architecture = json.architecture ?: platform.arch
        result.os = json.os ?: (platform.os ?: 'linux')
        if( json.variant )
            result.variant = json.variant
        return result
    }

    /**
     * @return The directory where the SOCI jobs work directories are created
     */
    static Path workspace(BuildConfig buildConfig) {
        final dir = buildConfig?.buildWorkspace ?: System.getProperty('java.io.tmpdir')
        return Path.of(dir).toAbsolutePath()
    }
}
