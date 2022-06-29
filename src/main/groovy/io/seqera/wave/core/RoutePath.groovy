package io.seqera.wave.core

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.service.ContainerRequestData

/**
 * Model a container registry route path
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class RoutePath {
    final String type
    final String registry
    final String image
    final String reference
    final String path
    final ContainerRequestData request

    boolean isManifest() { type=='manifests' }
    boolean isBlob() { type=='blobs' }
    boolean isTag() { reference && !isDigest() }
    boolean isDigest() { reference && reference.startsWith('sha256:') }

    static RoutePath v2path(String type, String registry, String image, String ref, ContainerRequestData request=null) {
        new RoutePath(type, registry, image, ref, "/v2/$image/$type/$ref", request)
    }

    static RoutePath empty() {
        new RoutePath(null, null, null, null, null, null)
    }
}
