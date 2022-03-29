package io.seqera

import groovy.transform.Canonical
import io.seqera.util.Base32

import java.util.regex.Matcher
import java.util.regex.Pattern

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

    static Route parse(String path) {
        Matcher matcher = ROUTE_PATHS.matcher(path)
        if( !matcher.matches() )
            return NOT_FOUND

        final String type = matcher.group(2)
        final String reference = matcher.group(3)
        final List<String> coordinates = matcher.group(1).tokenize('/')
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

        path = "/v2/$image/$type/$reference"

        return new Route(
                type,
                registry,
                image,
                reference,
                path
        )
    }

}
