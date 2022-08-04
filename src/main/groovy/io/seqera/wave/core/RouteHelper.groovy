package io.seqera.wave.core

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.ContainerTokenService
import io.seqera.wave.util.Base32
import jakarta.inject.Singleton

/**
 * Helper service to decode container request paths
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class RouteHelper {

    public static Pattern ROUTE_PATHS = ~'/v2(?:/tw|/wt)?/([a-z0-9][a-z0-9_.-]+(?:/[a-z0-9][a-z0-9_.-]+)?(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)*)/(manifests|blobs)/(.+)'

    public static RoutePath NOT_FOUND = RoutePath.empty()

    private ContainerTokenService tokenService

    RouteHelper(ContainerTokenService tokenService) {
        this.tokenService = tokenService
    }

    RoutePath parse(String path) {
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
            if( !request ) {
                log.warn "Token ${token} not found"
                return NOT_FOUND
            }
            // the image name (without tag) must match
            final coords = request.coordinates()
            if( image != coords.image )
                throw new IllegalArgumentException("Unexpected container image request '$image' does not match '${coords.image}' for token: $token")
            // compose the target request path in such a way that
            // - the 'registry' name is taken from the request associated to the token
            // - the 'reference' from the current request
            return RoutePath.v2path(type, coords.registry, coords.image, reference, request)
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

        return RoutePath.v2path(type, registry, image, reference)
    }

    private String normImage(String image) {
        image.contains('/') ? image : 'library/' + image
    }

}
