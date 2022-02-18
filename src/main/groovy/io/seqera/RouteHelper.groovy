package io.seqera

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.util.Base32

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RouteHelper {

    public static Pattern ROUTE_PATHS = ~'/v2/([a-zA-Z0-9][a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)?(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)?)/(manifests|blobs)/(.+)'

    public static Route NOT_FOUND = new Route(null, null,null,null)

    public static Pattern TOWER_PATHS = ~ "/v2/tw/([a-zA-Z0-9][a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)?(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)?)/(manifests|blobs)/(.+)"

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
        Matcher matcher

        if( (matcher=TOWER_PATHS.matcher(path)).matches() ) {
            String type = matcher.group(2)
            String reference = matcher.group(3)
            String coordinates = matcher.group(1)
            String encoded = coordinates.split('/')[0]
            String image = coordinates.split('/').length > 1 ? coordinates.split('/')[1] : ''
            String decoded = new String( Base32.decode(encoded))
            String registry = defaultRegistry
            if( decoded.indexOf('/') != -1){
                registry = decoded.split('/')[0]
                image = decoded.split('/')[1] + '/' +image
                path = "/v2/$image/$type/$reference"
            }
            return new Route(
                    type,
                    registry,
                    image,
                    reference,
                    path
            )
        }

        if( (matcher=ROUTE_PATHS.matcher(path)).matches() ) {
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
        else
            return NOT_FOUND
    }

}
