package io.seqera

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class RegHandler implements HttpHandler {

    private Cache cache = new Cache()
    private String username = "pditommaso"
    private String pat = 'd213e955-3357-4612-8c48-fa5652ad968b'


    @Override
    void handle(HttpExchange exchange) throws IOException {
        try {
            doHandle(exchange)
        }
        catch (Throwable e) {
            log.error("Unexpected error", e)
            handleError(exchange, e)
        }
    }

    protected void doHandle(HttpExchange exchange) {
        def path = exchange.getRequestURI().path
        def verb = exchange.requestMethod
        log.info "Request $verb - $path"

        // just for testing 
        if( path == '/ping' ) {
            handlePing(exchange)
            return
        }

        final route = RouteHelper.parse(path)
        final isHead = exchange.requestMethod=='HEAD'
        final isGet = exchange.requestMethod=='GET'
        if( route.isManifest() && route.isTag() && isHead ) {
            log.trace "Request manifest: $path"
            handleManifest(exchange, route)
        }
        else if( isGet && (route.isManifest() || route.isBlob()) ) {
            Cache.ResponseCache entry = cache.get(path)
            if( entry ) {
                log.trace "Cache request >> $path"
                handleCache(exchange, entry)
            }
            else {
                log.trace "Proxy request >> $path"
                handleProxy(exchange, client(route.image))
            }
        }
        else {
            log.trace "Request not found: $path"
            handleNotFound(exchange)
        }
    }

    private String dumpJson(payload) {
        if( payload==null )
            return '(null)'
        try {
            return '\n' + JsonOutput.prettyPrint(payload.toString())
        }
        catch( Throwable e ) {
            return '(no json output)'
        }
    }

    protected void handleProxy(HttpExchange exchange, ProxyClient proxy) {
        // forward request
        final resp = proxy.getStream(exchange.getRequestURI().path, exchange.getRequestHeaders())
        // copy response headers
        for( Map.Entry<String,List<String>> entry : resp.headers().map().entrySet() ) {
            for( String val : entry.value )
                exchange.responseHeaders.add(entry.key, val)
        }

        //
        int len = Integer.parseInt(resp.headers().firstValue('content-length').get())
        log.trace "Proxy response << status=${resp.statusCode()}; len=$len; content: ${dumpJson(resp.body())}"
        exchange.sendResponseHeaders( resp.statusCode(), len)

        // copy response
        final target = exchange.getResponseBody()
        resp.body().transferTo(target)
        target.close()
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

    @Memoized
    private ProxyClient client(String image) {
        new ProxyClient(username, pat, image)
    }

    @Memoized
    private ContainerScanner scanner(String image) {
        return new ContainerScanner()
                .withCache(cache)
                .withClient(client(image))
    }

    protected void handleManifest(HttpExchange exchange, RouteHelper.Route route) {

        // compute the injected digest
        final digest = scanner(route.image).resolve(route.image, route.reference, exchange.getRequestHeaders())
        if( digest == null )
            handleNotFound(exchange)

        // retries the cache entry generated from the resolve
        final req = "/v2/$route.image/manifests/$digest"
        final entry = cache.get(req)
        if( !entry )
            throw new IllegalStateException("Missing cached entry for request: $req")

        // return manifest list
        handleCache(exchange, entry)
    }

    protected void handleCache(HttpExchange exchange, Cache.ResponseCache entry) {
        int len = entry.bytes.length
        log.trace "Cache response << len=$len; content: ${dumpJson(new String(entry.bytes))}"
        def headers = new HashMap()
        headers.put("Content-Type", entry.mediaType)
        headers.put("docker-content-digest", entry.digest)
        headers.put("etag", entry.digest)
        headers.put("docker-distribution-api-version", "registry/2.0")

        // handle the final response
        handleResp0(exchange, entry.bytes, headers)
    }

    protected void handleResp0(HttpExchange exchange, byte[] body, Map headers) {
        Headers header = exchange.getResponseHeaders()
        for( Map.Entry<String,String> it : headers.entrySet() ) {
            header.set(it.key, it.value)
        }

        final head = exchange.requestMethod=='HEAD'
        if( head ) {
            // hack to specify `content-length` header for HEAD request
            // https://bugs.openjdk.java.net/browse/JDK-6886723
            // see https://github.com/prometheus/client_java/issues/685#issuecomment-917071851
            exchange.getResponseHeaders().add("Content-Length", body.length.toString())
            exchange.sendResponseHeaders(200, -1)
            // hack to prevent "response headers not sent yet" exception when closing the stream
            exchange.setStreams(null, new ByteArrayOutputStream(0))
            exchange.getResponseBody().close()
        }
        else {
            exchange.sendResponseHeaders(200, body.length)
            OutputStream os = exchange.getResponseBody()
            os.write(body)
            os.close();
        }
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
        handleResp0(exchange, message.bytes, ['Content-Type': '"text/plain"'])
    }

}
