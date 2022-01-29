package io.seqera

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RouteHelper {

    public static Pattern ROUTE_PATHS = ~'/v2/([a-zA-Z0-9][a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9][a-zA-Z0-9_.-]+)?)/(manifests|blobs)/(.+)'

    public static Route NOT_FOUND = new Route(null,null,null)

    @Canonical
    static class Route {
        String type
        String image
        String reference

        boolean isManifest() { type=='manifests' }
        boolean isBlob() { type=='blobs' }
        boolean isTag() { reference && !isDigest() }
        boolean isDigest() { reference && reference.startsWith('sha256:') }

    }

    static Route parse(String path) {
        Matcher matcher
        if( (matcher=ROUTE_PATHS.matcher(path)).matches() ) {
            return new Route(
                    matcher.group(2),
                    matcher.group(1),
                    matcher.group(3))
        }
        else
            return NOT_FOUND
    }

}
