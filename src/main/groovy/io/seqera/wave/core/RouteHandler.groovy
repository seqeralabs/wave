package io.seqera.wave.core

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.token.ContainerTokenService
import jakarta.inject.Singleton
/**
 * Helper service to decode container request paths
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class RouteHandler {

    final public static Pattern ROUTE_PATHS = ~'/v2(?:/wt)?/([a-z0-9][a-z0-9_.-]+(?:/[a-z0-9][a-z0-9_.-]+)?(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)*)/(manifests|blobs|tags)/(.+)'

    private ContainerTokenService tokenService

    RouteHandler(ContainerTokenService tokenService) {
        this.tokenService = tokenService
    }

    RoutePath parse(String path) {
        Matcher matcher = ROUTE_PATHS.matcher(path)
        if( !matcher.matches() )
            throw new NotFoundException("Invalid request path '$path'")

        final String type = matcher.group(2)
        final String reference = matcher.group(3)
        final List<String> parts = matcher.group(1).tokenize('/')

        if( path.startsWith('/v2/wt/') ) {
            // take the token that must be as first component after `/wt` prefix
            final token = parts.pop()
            final image = parts.join('/')
            // find out the container request that must have been submitted for the token
            final request = tokenService.getRequest(token)
            if( !request ) {
                throw new NotFoundException("Unknown Wave container for token '$token'")
            }
            // the image name (without tag) must match
            final coords = request.coordinates()
            if( image != coords.image )
                throw new IllegalArgumentException("Unexpected container image request '$image' does not match '${coords.image}' for token: $token")
            // compose the target request path in such a way that
            // - the 'registry' name is taken from the request associated to the token
            // - the 'reference' from the current request
            return RoutePath.v2path(type, coords.registry, coords.image, reference, request, token)
        }

        final String image
        final String registry

        registry = parts[0].contains('.') ? parts.pop() : null
        image = parts.join('/')

        return RoutePath.v2path(type, registry, image, reference)
    }

}
