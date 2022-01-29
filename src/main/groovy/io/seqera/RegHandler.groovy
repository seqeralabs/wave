package io.seqera

import java.net.http.HttpResponse

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class RegHandler implements HttpHandler {

    private ProxyClient proxy

    {
        def username = "pditommaso"
        def IMAGE = 'library/hello-world'
        def pat = 'd213e955-3357-4612-8c48-fa5652ad968b'
        and:
        proxy = new ProxyClient(IMAGE, username, pat)
    }
    
    @Override
    void handle(HttpExchange exchange) throws IOException {
        try {
            doHandle(exchange)
        }
        catch (Throwable e) {
            handleError(exchange, e)
        }
    }

    protected void doHandle(HttpExchange exchange) {
        def path = exchange.getRequestURI().path
        def verb = exchange.requestMethod
        log.info "Request $verb - $path"

        if( path == '/ping' ) {
            handlePing(exchange)
            return
        }

        final route = RouteHelper.parse(path)
        if( route.isManifest() && exchange.requestMethod in ['HEAD','GET'] ) {
            log.trace "Request manifest: $route"
            final head = 'HEAD' == exchange.requestMethod
            handleManifest(exchange, head, route)
        }
        else if( route.isBlob() ) {
            log.trace "Request blob: $route"
            handleBlob(exchange)
        }
        else {
            log.trace "Request not found: $route"
            handleNotFound(exchange)
        }
    }

    protected void handleError(HttpExchange exchange, Throwable e) {
        String message = "Unexpected server error: ${e.message}"
        Headers header = exchange.getResponseHeaders()
        header.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(500, message.size())

        OutputStream os = exchange.getResponseBody();
        os.write(message.bytes);
        os.close();
    }

    protected void handleManifest(HttpExchange exchange, boolean head, RouteHelper.Route route) {
        if( route.isTag() || route.reference == 'sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800' ) {
            // return manifest list
            final manifest = Mock.MANIFEST_LIST_CONTENT
            final digest = RegHelper.digest(manifest)
            final headers = new HashMap<String,String>()
            headers.put("Content-Type", Mock.MANIFEST_LIST_MIME)
            headers.put("docker-content-digest", digest)
            headers.put("etag", digest)
            headers.put("docker-distribution-api-version", "registry/2.0")

            // handle response
            handleManifest0(exchange, head, headers, manifest)
        }
        else if( route.reference == 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4' ) {
            // return manifest list
            final manifest = Mock.MANIFEST_CONTENT
            final digest = RegHelper.digest(manifest)
            final headers = new HashMap<String,String>()
            headers.put("Content-Type", Mock.MANIFEST_MIME)
            headers.put("docker-content-digest", digest)
            headers.put("etag", digest)
            headers.put("docker-distribution-api-version", "registry/2.0")

            // handle response
            handleManifest0(exchange, head, headers, manifest)
        }
        else if( exchange.requestMethod == 'GET' ) {
            // forward request
            final resp = proxy.getStream(exchange.getRequestURI().path, exchange.getRequestHeaders())
            // copy response
            handleManifest0(exchange, resp)
        }
        else {
            handleNotFound(exchange)
        }

    }

    protected void handleManifest0(HttpExchange exchange, boolean head, Map headers, String body) {
        Headers header = exchange.getResponseHeaders()
        for( Map.Entry<String,String> it : headers.entrySet() ) {
            header.set(it.key, it.value)
        }

        if( head ) {
            // hack to specify `content-length` header for HEAD request
            // https://bugs.openjdk.java.net/browse/JDK-6886723
            // see https://github.com/prometheus/client_java/issues/685#issuecomment-917071851
            exchange.getResponseHeaders().add("Content-Length", body.bytes.length.toString())
            exchange.sendResponseHeaders(200, 0)
            exchange.getResponseBody().close()
        }
        else {
            exchange.sendResponseHeaders(200, body.bytes.length)
            OutputStream os = exchange.getResponseBody()
            os.write(body.bytes)
            os.close();
        }
    }

    protected void handleManifest0(HttpExchange exchange, HttpResponse<InputStream> resp) {
        // copy response headers
        for( Map.Entry<String,List<String>> entry : resp.headers().map().entrySet() ) {
            for( String val : entry.value )
                exchange.responseHeaders.add(entry.key, val)
        }
        // copy response
        final target = exchange.getResponseBody()
        final len = resp.body().transferTo(target)
        target.close()
        //
        exchange.sendResponseHeaders( resp.statusCode(), len)
    }

    protected void handleBlob(HttpExchange exchange) {

    }
    
    protected void handleNotFound(HttpExchange exchange) {
        final message = 'Not found'
        Headers header = exchange.getResponseHeaders()
        header.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(404, message.size())

        OutputStream os = exchange.getResponseBody();
        os.write(message.bytes);
        os.close();
    }

    protected void handlePing(HttpExchange exchange) {
        final message = 'pong'
        Headers header = exchange.getResponseHeaders()
        header.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(200, message.size())

        OutputStream os = exchange.getResponseBody();
        os.write(message.bytes);
        os.close();
    }

}
