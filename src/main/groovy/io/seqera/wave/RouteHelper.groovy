package io.seqera.wave

import groovy.transform.Canonical
import groovy.transform.ToString
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.ContainerTokenService
import io.seqera.wave.util.Base32

import java.util.regex.Matcher
import java.util.regex.Pattern

import jakarta.inject.Singleton

/**
 * Helper service to decode container request paths
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class RouteHelper {

    public static Pattern ROUTE_PATHS = ~'/v2(?:/tw|/wt)?/([a-z0-9][a-z0-9_.-]+(?:/[a-z0-9][a-z0-9_.-]+)?(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)*)/(manifests|blobs)/(.+)'

    public static Route NOT_FOUND = Route.empty()

    private ContainerTokenService tokenService

    RouteHelper(ContainerTokenService tokenService) {
        this.tokenService = tokenService
    }

    @Canonical
    @ToString(includePackage = false, includeNames = true)
    static class Route {
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

        static Route v2path(String type, String registry, String image, String ref, ContainerRequestData request=null) {
            new Route(type, registry, image, ref, "/v2/$image/$type/$ref", request)
        }

        static Route empty() {
            new Route(null, null, null, null, null, null)
        }
    }

    Route parse(String path) {
        Matcher matcher = ROUTE_PATHS.matcher(path)
        if( !matcher.matches() )
            return NOT_FOUND

        final String type = matcher.group(2)
        final String reference = matcher.group(3)
        final List<String> coordinates = matcher.group(1).tokenize('/')

        if( path.startsWith('/v2/wt/') ) {
            // take the token that must be as first component after `/wt` prefix
            final token = coordinates[0]; coordinates.remove(0)
            final image = normImage(coordinates.join('/'))
            // find out the container request that must have been submitted for the token
            final request = tokenService.getRequest(token)
            if( !request )
                return NOT_FOUND
            // the image name (without tag) must match
            final coords = request.coordinates()
            if( image != coords.image )
                throw new IllegalArgumentException("Unexpected container image request '$image' does not match '${coords.image}' for token: $token")
            // compose the target request path in such a way that
            // - the 'registry' name is taken from the request associated to the token
            // - the 'reference' from the current request
            return Route.v2path(type, coords.registry, coords.image, reference, request)
        }

        final String image
        final String registry

        if( path.startsWith('/v2/tw/') ) {
            String encoded = coordinates[0]
            List<String> decoded = new String(Base32.decode(encoded)).tokenize('/')
            coordinates[0] = decoded.first()
            if( decoded.size() > 1) {
                coordinates.add(1, decoded.drop(1).join('/'))
            }
        }

        registry = coordinates[0].contains('.') ? coordinates.pop() : null
        image = coordinates.join('/')

        return Route.v2path(type, registry, image, reference)
    }

    private String normImage(String image) {
        image.contains('/') ? image : 'library/' + image
    }

}
