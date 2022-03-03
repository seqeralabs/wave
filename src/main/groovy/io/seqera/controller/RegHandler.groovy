package io.seqera.controller

import static io.seqera.controller.RegHelper.*

import java.net.http.HttpResponse

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.transform.Memoized
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j
import io.seqera.Cache
import io.seqera.ContainerScanner
import io.seqera.RouteHelper
import io.seqera.auth.AuthFactory
import io.seqera.auth.DockerAuthProvider
import io.seqera.config.Registry
import io.seqera.config.TowerConfiguration
import io.seqera.proxy.InvalidResponseException
import io.seqera.proxy.ProxyClient
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
        catch (InvalidResponseException e) {
            handleReply(exchange, e.response)
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

        if( !path.startsWith('/v2') ) {
            log.trace "Request not found: $path"
            handleNotFound(exchange)
        }

        if( path=='/v2' || path=='/v2/' || path=='/' ) {
            handleOK(exchange)
            return
        }

        final route = RouteHelper.parse(path, configuration.defaultRegistry.name)
        final Registry registry = configuration.findRegistry(route.registry)
        assert registry

        final isHead = exchange.requestMethod=='HEAD'
        final isGet = exchange.requestMethod=='GET'

        ProxyClient proxyClient = client(registry, route.image)

        if( route.isManifest() && route.isTag() && isHead ) {
            log.trace "Request manifest: $route.path"
            handleManifest(exchange, route, proxyClient)
            return
        }

        if( isGet ){
            handleGet(exchange, route, proxyClient)
            return
        }

        log.trace "Request not found: $route.path"
        handleNotFound(exchange)
    }

    protected void handleProxy(String path, HttpExchange exchange, ProxyClient proxy) {
        // forward request
        final resp = proxy.getStream(path, exchange.getRequestHeaders())
        // forward response
        handleReply(exchange, resp)
    }

    protected void handleReply(HttpExchange exchange, HttpResponse<InputStream> resp) {
        // copy response headers
        for( Map.Entry<String,List<String>> entry : resp.headers().map().entrySet() ) {
            for( String val : entry.value )
                exchange.responseHeaders.add(entry.key, val)
        }

        //
        int len = Integer.parseInt(resp.headers().firstValue('content-length').orElse('-1'))
        log.trace "Proxy response << status=${resp.statusCode()}; len=$len; content: ${dumpJson(resp.body())}"
        exchange.sendResponseHeaders( resp.statusCode(), len)

        if( len>0 ) {
            // copy response
            final target = exchange.getResponseBody()
            resp.body().transferTo(target)
            target.close()
        }
        else {
            // hack to prevent "response headers not sent yet" exception when closing the stream
            exchange.setStreams(null, new ByteArrayOutputStream(0))
            exchange.getResponseBody().close()
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

    protected void handleOK(HttpExchange exchange) {
        def headers = new HashMap<String,String>()
        headers.put("Content-Type", 'text/plain')
        headers.put("docker-distribution-api-version", "registry/2.0")
        // handle the final response
        handleResp1(exchange, 200, 'OK'.bytes, headers)
    }

    @Memoized
    private ProxyClient client(Registry registry, String image) {
        DockerAuthProvider authProvider = authFactory.getProvider(registry)
        new ProxyClient(registry.host, image, authProvider)
    }

    @Memoized
    private ContainerScanner scanner(ProxyClient proxyClient) {
        return new ContainerScanner()
                .withArch(configuration.arch)
                .withCache(cache)
                .withClient(proxyClient)
    }

    protected void handleManifest(HttpExchange exchange, RouteHelper.Route route, ProxyClient proxyClient) {

        // compute the injected digest
        final digest = scanner(proxyClient).resolve(route.image, route.reference, exchange.getRequestHeaders())
        if( digest == null )
            throw new IllegalStateException("Missing digest for request: $route")

        // retries the cache entry generated from the resolve
        final req = "/v2/$route.image/manifests/$digest"
        final entry = cache.get(req)
        if( !entry )
            throw new IllegalStateException("Missing cached entry for request: $req")

        // return manifest list
        handleCache(exchange, entry)
    }

    protected void handleGet(HttpExchange exchange, RouteHelper.Route route, ProxyClient proxyClient) {

        if( !(route.isManifest() || route.isBlob() ) ){
            handleNotFound(exchange)
            return
        }
        Cache.ResponseCache entry = cache.get(route.path)
        if( entry ) {
            log.trace "Cache request >> $route.path"
            handleCache(exchange, entry)
            return
        }

        log.trace "Proxy request >> $route.path"
        handleProxy(route.path, exchange, proxyClient)
    }

    protected void handleCache(HttpExchange exchange, Cache.ResponseCache entry) {
        int len = entry.bytes.length
        log.trace "Cache response << len=$len; content: ${dumpJson(new String(entry.bytes))}"
        def headers = new HashMap<String,String>()
        headers.put("Content-Type", entry.mediaType)
        headers.put("docker-content-digest", entry.digest)
        headers.put("etag", entry.digest)
        headers.put("docker-distribution-api-version", "registry/2.0")

        // handle the final response
        handleResp1(exchange, 200, entry.bytes, headers)
    }

    protected void handleResp1(HttpExchange exchange, int status, byte[] body, Map<String,String> headers) {
        final copy = new HashMap<String,List<String>>(10)
        headers.each { k,v -> copy.put(k, [v]) }
        handleResp0(exchange, status, body, copy)
    }

    protected void handleResp0(HttpExchange exchange, int status, byte[] body, Map<String,List<String>> headers) {
        Headers header = exchange.getResponseHeaders()
        for( Map.Entry<String,List<String>> it : headers.entrySet() ) {
            for( String val : it.value ) {
                header.set(it.key, val)
            }
        }

        final head = exchange.requestMethod=='HEAD'
        if( head ) {
            // hack to specify `content-length` header for HEAD request
            // https://bugs.openjdk.java.net/browse/JDK-6886723
            // see https://github.com/prometheus/client_java/issues/685#issuecomment-917071851
            exchange.getResponseHeaders().add("Content-Length", body.length.toString())
            exchange.sendResponseHeaders(status, -1)
            // hack to prevent "response headers not sent yet" exception when closing the stream
            exchange.setStreams(null, new ByteArrayOutputStream(0))
            exchange.getResponseBody().close()
        }
        else {
            exchange.sendResponseHeaders(status, body.length)
            OutputStream os = exchange.getResponseBody()
            os.write(body)
            os.close();
        }
    }


    protected void handleNotFound(HttpExchange exchange) {
        final byte[] message = 'Not found'.bytes
        Headers headers = exchange.getResponseHeaders()
        headers.set("Content-Type", "text/plain")
        // send response
        handleResp0(exchange, 404, message, headers)
    }

    protected void handlePing(HttpExchange exchange) {
        final message = 'pong'
        handleResp1(exchange, 200, message.bytes, ['Content-Type': '"text/plain"'])
    }

}
