package io.seqera.proxy

import io.seqera.auth.DockerAuthProvider

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.time.Duration

import groovy.util.logging.Slf4j
import io.seqera.controller.RegHelper

/**
 *
 * https://www.baeldung.com/java-9-http-client
 * https://openjdk.java.net/groups/net/httpclient/recipes.html
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class ProxyClient {

    private String image

    DockerAuthProvider authProvider
    private String registryName
    private HttpClient httpClient

    ProxyClient(String registryName, String image, DockerAuthProvider authProvider) {
        this.image = image
        this.registryName = registryName
        this.authProvider = authProvider
        init()
    }

    private void init() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build()
    }

    private URI makeUri(String path) {
        assert path.startsWith('/'), "Request past should start with a slash character -- offending path: $path"
        URI.create("https://$registryName$path")
    }

    HttpResponse<String> getString(String path, Map<String,List<String>> headers=null) {
        return get( makeUri(path), headers, HttpResponse.BodyHandlers.ofString() )
    }

    HttpResponse<InputStream> getStream(String path, Map<String,List<String>> headers=null) {
        return get( makeUri(path), headers, HttpResponse.BodyHandlers.ofInputStream() )
    }

    HttpResponse<byte[]> getBytes(String path, Map<String,List<String>> headers=null) {
        return get( makeUri(path), headers, HttpResponse.BodyHandlers.ofByteArray() )
    }


    private static final List<String> SKIP_HEADERS = ['host', 'connection']

    private void copyHeaders(Map<String,List<String>> headers, HttpRequest.Builder builder) {
        if( !headers )
            return

        for( Map.Entry<String,List<String>> entry : headers )  {
            if( entry.key.toLowerCase() in SKIP_HEADERS )
                continue
            for( String val : entry.value )
                builder.header(entry.key, val)
        }
    }

    private static final int[] REDIRECT_CODES = [301, 302, 307, 308]

    def <T> HttpResponse<T> get(URI uri, Map<String,List<String>> headers, BodyHandler<T> handler) {
        def visited = new HashSet<URI>(10)
        def target = uri
        int redirectCount = 0
        int authRetry = 0
        while( true ) {
            visited.add(target)
            def result = get0(target, headers, handler)
            if( result.statusCode()==401 && (authRetry++)<2 ) {
                // clear the token to force refreshing it
                authProvider.cleanTokenFor(image)
                continue
            }
            if( result.statusCode() in REDIRECT_CODES  ) {
                final redirect = result.headers().firstValue('location').orElse(null)
                log.trace "Redirecting (${++redirectCount}) $target ==> $redirect ${RegHelper.dumpHeaders(result.headers())}"
                target = new URI(redirect)
                if( target in visited ) {
                    log.warn("Redirect location already visited: $redirect")
                }
                continue
            }
            return result
        }

    }

    private <T> HttpResponse<T> get0(URI uri, Map<String,List<String>> headers, BodyHandler<T> handler) {
        final builder = HttpRequest.newBuilder(uri) .GET()
        copyHeaders(headers, builder)
        // add authorisation header
        String token = authProvider.getTokenFor(image)
        builder.setHeader("Authorization", "Bearer ${token}")
        // build the request
        final request = builder.build()
        // send it
        return httpClient.send(request, handler)
    }

    HttpResponse<Void> head(String path, Map<String,List<String>> headers=null) {
        return head(makeUri(path), headers)
    }

    HttpResponse<Void> head(URI uri, Map<String,List<String>> headers) {
        def result = head0(uri, headers)
        if( result.statusCode()==401 ) {
            // clear the token to force refreshing it
            authProvider.cleanTokenFor(image)
            result = head0(uri, headers)
        }
        return result
    }

    HttpResponse<Void> head0(URI uri, Map<String,List<String>> headers) {
        // A HEAD request is a GET request without a message body
        // https://zetcode.com/java/httpclient/
        final builder = HttpRequest.newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
        // copy headers 
        copyHeaders(headers, builder)
        // add authorisation header
        String token = authProvider.getTokenFor(image)
        builder.setHeader("Authorization", "Bearer ${token}")
        // build the request
        final request = builder.build()
        // send it 
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    }


}
