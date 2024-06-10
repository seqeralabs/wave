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

package io.seqera.wave.core

import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.tower.PlatformId
import static io.seqera.wave.WaveDefault.DOCKER_IO
/**
 * Model a container registry route path
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class RoutePath implements ContainerPath {

    static final private List<String> ALLOWED_TYPES = ['manifests','blobs','tags']

    static final private Pattern REGEX = ~/([^:\\/]+(?::[0-9]+)?)?\\/v2\\/([a-z0-9][a-z0-9_.-]+(?:\\/[a-z0-9][a-z0-9_.-]+)?(?:\[a-zA-Z0-9][a-zA-Z0-9_.-]+)*)\\/(manifests|blobs)\\/(.+)/

    /**
     * Route type, either {@code manifests} or {@code blobs}
     */
    final String type

    /**
     * The container registry name e.g. {@code docker.io}
     */
    final String registry

    /**
     * The container image name without the tag or reference component e.g. {@code library/ubuntu}
     */
    final String image

    /**
     * The tag or sha256 checksum of the requested container
     */
    final String reference

    /**
     * The URI path of the corresponding container image e.g. {@code /v2/library/ubuntu/manifests/latest}
     */
    final String path

    /**
     * The {@link ContainerRequestData} metadata associated with the wave request
     */
    final ContainerRequestData request

    /**
     * The unique token associated with the wave container request. it may be null when mapping
     * a non-wave container request. 
     */
    @Nullable
    final String token

    boolean isManifest() { type=='manifests' }
    boolean isBlob() { type=='blobs' }
    boolean isTagList() { type=='tags' && reference=='list' }
    boolean isTag() { type!='tags' && reference && !isDigest() }
    boolean isDigest() { reference && reference.startsWith('sha256:') }

    PlatformId getIdentity() {
        request?.identity ?: PlatformId.NULL
    }

    String getRepository() { "$registry/$image" }

    String getTargetContainer() { registry + '/' + getImageAndTag() }

    String getImageAndTag() {
        if( !reference ) return image
        final sep = isDigest() ? '@' : ':'
        return image + sep + reference
    }

    String getToken() {
        return token
    }

    String getTargetPath() {
        return registry + path
    }

    /**
     * This method define when the request is an unresolved wave container path that is:
     * - should contain a wave token
     * - should be a manifest path
     * - should be a tag not (not a digest)
     *
     * @return {@true} whenever is a Wave unresolved manifest request or {@code false} otherwise
     */
    boolean isUnresolvedManifest() {
        return token && isManifest() && isTag()
    }

    static RoutePath v2path(String type, String registry, String image, String ref, ContainerRequestData request=null, String token=null) {
        assert type in ALLOWED_TYPES, "Unknown container path type: '$type'"
        new RoutePath(type, registry ?: DOCKER_IO, image, ref, "/v2/$image/$type/$ref", request, token)
    }

    static RoutePath v2manifestPath(ContainerCoordinates container) {
        new RoutePath('manifests', container.registry, container.image, container.reference, "/v2/${container.image}/manifests/${container.reference}")
    }

    static RoutePath empty() {
        new RoutePath(null, null, null, null, null)
    }

    static RoutePath parse(String location, PlatformId identity=null) {
        assert location, "Missing 'location' attribute"
        if( location.startsWith('docker://') )
            location = location.substring(9)

        final m = REGEX.matcher(location)
        if( m.matches() ) {
            final registry = m.group(1)
            final image = m.group(2)
            final type = m.group(3)
            final reference = m.group(4)
            final data = identity!=null ? new ContainerRequestData(identity) : null
            return v2path(type, registry, image, reference, data)
        }
        else
            throw new IllegalArgumentException("Not a valid container path - offending value: '$location'")
    }
}
