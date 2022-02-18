package io.seqera

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.json.JsonOutput
import groovy.transform.Memoized
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j
import io.seqera.config.Registry
import io.seqera.config.TowerConfiguration
import io.seqera.docker.AuthFactory
import io.seqera.docker.DockerAuthProvider

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Builder
class RegHandler implements HttpHandler {

    private Cache cache = new Cache()

    TowerConfiguration configuration

    AuthFactory authFactory

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

        if( path=='/v2' || path=='/v2/' ) {
            handleOK(exchange)
            return
        }

        final route = RouteHelper.parse(path, configuration.defaultRegistry.name)
        final Registry registry = configuration.findRegistry(route.registry)
        assert registry

        final isHead = exchange.requestMethod=='HEAD'
        final isGet = exchange.requestMethod=='GET'
        if( route.isManifest() && route.isTag() ) {
            log.trace "Request manifest: $route.path"
            handleManifest(exchange, registry, route.image, route.reference)
        }
        else if( isGet && (route.isManifest() || route.isBlob()) ) {
            Cache.ResponseCache entry = cache.get(route.path)
            if( entry ) {
                log.trace "Cache request >> $route.path"
                handleCache(exchange, entry)
            }
            else {
                log.trace "Proxy request >> $route.path"
                handleProxy(route.path, exchange, client(registry, route.image))
            }
        }
        else {
            log.trace "Request not found: $route.path"
            handleNotFound(exchange)
        }
    }

    private String dumpJson(payload) {
        if( payload==null )
            return '(null)'
        try {
            return '\n' + JsonOutput.prettyPrint(payload.toString().trim())
        }
        catch( Throwable e ) {
            return '(no json output)'
        }
    }

    protected void handleProxy(String path, HttpExchange exchange, ProxyClient proxy) {
        // forward request
        final resp = proxy.getStream(path, exchange.getRequestHeaders())
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

    protected void handleOK(HttpExchange exchange) {
        def headers = new HashMap()
        headers.put("Content-Type", 'text/plain')
        headers.put("docker-distribution-api-version", "registry/2.0")
        // handle the final response
        handleResp0(exchange, 'OK'.bytes, headers)
    }

    @Memoized
    private ProxyClient client(Registry registry, String image) {
        DockerAuthProvider authProvider = authFactory.getProvider(registry)
        new ProxyClient(registry.host, image, authProvider)
    }

    @Memoized
    private ContainerScanner scanner(Registry registry, String image) {
        return new ContainerScanner()
                .withArch(configuration.arch)
                .withCache(cache)
                .withClient(client(registry, image))
    }

    protected void handleManifest(HttpExchange exchange, Registry registry, String image, String reference) {

        // compute the injected digest
        final digest = scanner(registry, image).resolve(image, reference, exchange.getRequestHeaders())
        if( digest == null )
            handleNotFound(exchange)

        // retries the cache entry generated from the resolve
        final req = "/v2/$image/manifests/$digest"
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
        final byte[] message = 'Not found'.bytes
        Headers header = exchange.getResponseHeaders()
        header.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(404, message.length)

        OutputStream os = exchange.getResponseBody();
        os.write(message);
        os.close();
    }

    protected void handlePing(HttpExchange exchange) {
        final message = 'pong'
        handleResp0(exchange, message.bytes, ['Content-Type': '"text/plain"'])
    }

}
