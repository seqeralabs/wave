/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.service.builder

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.WaveDefault.ACCEPT_HEADERS
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
/**
 * Assembles an OCI Image Index (manifest list) from individual
 * platform-specific manifests and pushes it to the registry.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ManifestAssembler {

    @Inject
    RegistryLookupService registryLookup

    @Inject
    RegistryCredentialsProvider credentialsProvider

    @Inject
    RegistryAuthService loginService

    @Inject
    HttpClientConfig httpConfig

    /**
     * Create and push an OCI Image Index (manifest list) to the registry.
     *
     * @param targetImage The final tag for the manifest list (e.g. repo:tag)
     * @param platformImages List of platform-specific images [amd64-image, arm64-image]
     * @param identity The platform identity for credentials lookup
     */
    void createAndPushManifestList(String targetImage, List<Map> platformEntries, PlatformId identity) {
        log.debug "Creating manifest list for targetImage=$targetImage from platformEntries=$platformEntries"

        // 1. Fetch each platform manifest
        final manifests = platformEntries.collect { Map entry -> fetchPlatformManifest(entry.image as String, entry.platform as ContainerPlatform, identity) }

        // 2. Build the OCI Image Index JSON
        final indexJson = buildImageIndex(manifests)
        log.debug "OCI Image Index JSON: $indexJson"

        // 3. Push the index to the registry under the target tag
        pushManifest(targetImage, indexJson, identity)
        log.info "Successfully pushed manifest list for targetImage=$targetImage"
    }

    protected Map fetchPlatformManifest(String image, ContainerPlatform platform, PlatformId identity) {
        final coords = ContainerCoordinates.parse(image)
        final route = RoutePath.v2manifestPath(coords, identity)
        final client = createClient(route, identity, false)

        final resp = client.getString(route.path, ACCEPT_HEADERS)
        if( resp.statusCode() != 200 )
            throw new IllegalStateException("Failed to GET manifest for '$image' — status: ${resp.statusCode()}, body: ${resp.body()}")

        final body = resp.body()
        final contentType = resp.headers().firstValue('content-type').orElse(OCI_IMAGE_MANIFEST_V1)
        final digest = resp.headers().firstValue('docker-content-digest').orElse(null)
        final size = body.bytes.length

        return [
            mediaType: contentType,
            digest: digest,
            size: size,
            platform: [architecture: platform.arch, os: platform.os]
        ]
    }

    static String buildImageIndex(List<Map> manifests) {
        final index = [
            schemaVersion: 2,
            mediaType: OCI_IMAGE_INDEX_V1,
            manifests: manifests.collect { Map m ->
                [
                    mediaType: m.mediaType,
                    digest: m.digest,
                    size: m.size,
                    platform: m.platform
                ]
            }
        ]
        return JsonOutput.prettyPrint(JsonOutput.toJson(index))
    }

    protected void pushManifest(String targetImage, String indexJson, PlatformId identity) {
        final coords = ContainerCoordinates.parse(targetImage)
        final route = RoutePath.v2manifestPath(coords, identity)
        final client = createClient(route, identity, true)

        final body = indexJson.bytes
        final path = "/v2/${coords.image}/manifests/${coords.reference}"
        final resp = client.put(path, body, OCI_IMAGE_INDEX_V1)

        if( resp.statusCode() != 201 && resp.statusCode() != 200 ) {
            throw new IllegalStateException("Failed to PUT manifest list for '$targetImage' — status: ${resp.statusCode()}, body: ${resp.body()}")
        }
        log.debug "PUT manifest list for '$targetImage' — status: ${resp.statusCode()}"
    }

    private ProxyClient createClient(RoutePath route, PlatformId identity, boolean pushMode) {
        final httpClient = HttpClientFactory.neverRedirectsHttpClient()
        final registry = registryLookup.lookup(route.registry)
        final creds = credentialsProvider.getCredentials(route, identity)
        new ProxyClient(httpClient, httpConfig)
                .withRoute(route)
                .withImage(route.image)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
                .withPushMode(pushMode)
    }
}
