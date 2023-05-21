package io.seqera.wave.core

import javax.annotation.Nullable

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestData

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

    boolean isUnresolved() {
        return token && isTag()
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
}
