package io.seqera

import groovy.transform.EqualsAndHashCode

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Canonical
import io.seqera.util.Base32
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RouteHelper {

    public static Pattern ROUTE_PATHS = ~'/v2(?:/tw)?/([a-z0-9][a-z0-9_.-]+(?:/[a-z0-9][a-z0-9_.-]+)?(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)?)/(manifests|blobs)/(.+)'

    public static Route NOT_FOUND = new Route(null, null,null,null)

    @Canonical
    static class Route {
        String type
        String registry
        String image
        String reference
        String path

        boolean isManifest() { type=='manifests' }
        boolean isBlob() { type=='blobs' }
        boolean isTag() { reference && !isDigest() }
        boolean isDigest() { reference && reference.startsWith('sha256:') }

    }

    static Route parse(String path, String defaultRegistry) {
        Matcher matcher = ROUTE_PATHS.matcher(path)
        if( !matcher.matches() )
            return NOT_FOUND

        if( path.startsWith('/v2/tw/') ) {
            String type = matcher.group(2)
            String reference = matcher.group(3)
            String coordinates = matcher.group(1)
            String encoded = coordinates.split('/')[0]
            String image = coordinates.split('/').length > 1 ? coordinates.split('/')[1] : ''
            String decoded = new String( Base32.decode(encoded))
            String registry = defaultRegistry
            final elems = decoded.tokenize('/')
            if( elems[0].contains('.') ) {
                // since contains a dot, it must a registry name
                registry = elems.pop()
            }
            image = "${elems ? elems.join('/') : 'library'}/$image"
            path = "/v2/$image/$type/$reference"

            return new Route(
                    type,
                    registry,
                    image,
                    reference,
                    path
            )
        }
        else {
            String image = matcher.group(1)
            String registry = defaultRegistry
            return new Route(
                    matcher.group(2),
                    registry,
                    image,
                    matcher.group(3),
                    path
            )
        }

    }

}
