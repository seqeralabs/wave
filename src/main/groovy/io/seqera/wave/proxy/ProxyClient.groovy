/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.proxy

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpMethod
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.server.exceptions.InternalServerException
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient
import io.seqera.wave.exception.RegistryForwardException
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.exception.RegistryUnauthorizedAccessException
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.util.RegHelper
import io.seqera.wave.util.Retryable
import reactor.core.publisher.Flux
import static io.seqera.wave.WaveDefault.HTTP_REDIRECT_CODES
import static io.seqera.wave.WaveDefault.HTTP_RETRYABLE_ERRORS
/**
 *
 * https://www.baeldung.com/java-9-http-client
 * https://openjdk.java.net/groups/net/httpclient/recipes.html
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class ProxyClient {

    private String image
    private RegistryInfo registry
    private RegistryCredentials credentials
    private HttpClient httpClient
    private RegistryAuthService loginService
    private ContainerPath route
    private HttpClientConfig httpConfig
    private List<Integer> retryableHttpErrors = HTTP_RETRYABLE_ERRORS

    ProxyClient(HttpClient httpClient, HttpClientConfig httpConfig) {
        if( httpClient.followRedirects()!= HttpClient.Redirect.NEVER )
            throw new IllegalStateException("HttpClient instance should not follow redirected because they are directly managed by the proxy")
        this.httpClient = httpClient
        this.httpConfig = httpConfig
    }

    ContainerPath getRoute() { route }

    RegistryInfo getRegistry() { registry }

    ProxyClient withRoute(ContainerPath route) {
        this.route = route
        return this
    }

    ProxyClient withImage(String image) {
        this.image = image
        return this
    }

    ProxyClient withRegistry(RegistryInfo registry) {
        this.registry = registry
        return this
    }

    ProxyClient withLoginService(RegistryAuthService loginService) {
        this.loginService = loginService
        return this
    }

    ProxyClient withCredentials(RegistryCredentials credentials) {
        this.credentials = credentials
        return this
    }

    ProxyClient withRetryableHttpErrors(List<Integer> errors) {
        if( errors!=null )
            retryableHttpErrors = errors
        return this
    }

    URI makeUri(String path) {
        assert path.startsWith('/'), "Request past should start with a slash character — offending path: $path"
        return URI.create(registry.host.toString() + path)
    }

    HttpResponse<String> getString(String path, Map<String,List<String>> headers=null, boolean followRedirect=true) {
        try {
            return get( makeUri(path), headers, HttpResponse.BodyHandlers.ofString(), followRedirect )
        }
        catch (ClientResponseException e) {
            return ErrResponse<String>.forString(e.message, e.request)
        }
    }

    HttpResponse<InputStream> getStream(String path, Map<String,List<String>> headers=null, boolean followRedirect=true) {
        try {
            return get( makeUri(path), headers, HttpResponse.BodyHandlers.ofInputStream(), followRedirect )
        }
        catch (ClientResponseException e) {
            return ErrResponse.forStream(e.message, e.request)
        }
    }

    HttpResponse<byte[]> getBytes(String path, Map<String,List<String>> headers=null, boolean followRedirect=true) {
        try {
            return get( makeUri(path), headers, HttpResponse.BodyHandlers.ofByteArray(), followRedirect )
        }
        catch (ClientResponseException e) {
            return ErrResponse.forByteArray(e.message, e.request)
        }
    }

    private static final List<String> SKIP_HEADERS = ['host', 'connection', 'authorization', 'content-length']

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

    private void copyHeaders(Map<String,List<String>> headers, MutableHttpRequest request) {
        if( !headers )
            return

        for( Map.Entry<String,List<String>> entry : headers )  {
            if( entry.key.toLowerCase() in SKIP_HEADERS )
                continue
            for( String val : entry.value )
                request.header(entry.key, val)
        }
    }

    def <T> HttpResponse<T> get(URI origin, Map<String,List<String>> headers, BodyHandler<T> handler, boolean followRedirect) {
        final retryable = Retryable
                .<HttpResponse<T>>of(httpConfig)
                .retryIf((resp) -> resp.statusCode() in retryableHttpErrors)
                .onRetry((event) -> "Failure on GET request: $origin - event: $event")
        // carry out the request
        return retryable.apply(()-> get0(origin, headers, handler, followRedirect))
    }

    def <T> HttpResponse<T> get0(URI origin, Map<String,List<String>> headers, BodyHandler<T> handler, boolean followRedirect) {
        final host = origin.getHost()
        final visited = new HashSet<URI>(10)
        def target = origin
        int redirectCount = 0
        int authRetry = 0
        while( true ) {
            // add authorization header ONLY when the target host
            // is different from the origin host, otherwise it can
            // cause an error when the redirection target uses a different
            // auth mechanism e.g. signed url
            // https://github.com/rkt/rkt/issues/2266#issuecomment-211326020
            final authorize = host == target.getHost()
            final result = get1(target, headers, handler, authorize)
            // add to visited URL
            visited.add(target)
            // check the result code
            if( result.statusCode()==401 && (authRetry++)<2 && host==target.host && registry.auth.isRefreshable() ) {
                // clear the token to force refreshing it
                loginService.invalidateAuthorization(image, registry.auth, credentials)
                continue
            }
            if( result.statusCode() in HTTP_REDIRECT_CODES && followRedirect ) {
                final redirect = result.headers().firstValue('location').orElse(null)
                log.trace "Redirecting (${++redirectCount}) $target ==> $redirect ${RegHelper.dumpHeaders(result.headers())}"
                if( !redirect ) {
                    final msg = "Missing `Location` header for request URI '$target' ― origin request '$origin'"
                    throw new ClientResponseException(msg, result.request())
                }
                // the redirect location can be a relative path i.e. without hostname
                // therefore resolve it against the target registry hostname
                target = registry.host.resolve(redirect)
                if( target in visited ) {
                    final msg = "Redirect location already visited: $redirect ― origin request '$origin'"
                    throw new ClientResponseException(msg, result.request())
                }
                if( visited.size()>=10 ) {
                    final msg = "Redirect location already visited: $redirect ― origin request '$origin'"
                    throw new ClientResponseException(msg, result.request())
                }
                // go head with with the redirection
                continue
            }
            return result
        }

    }

    private <T> HttpResponse<T> get1(URI uri, Map<String,List<String>> headers, BodyHandler<T> handler, boolean authorize) {
        try{
            final builder = HttpRequest.newBuilder(uri) .GET()
            copyHeaders(headers, builder)
            if( authorize ) {
                // add authorisation header
                final header = loginService.getAuthorization(image, registry.auth, credentials)
                if( header )
                    builder.setHeader("Authorization", header)
            }
            // build the request
            final request = builder.build()
            // send it
            final response = httpClient.send(request, handler)
            traceResponse(response)
            return response
        }
        catch (IOException e) {
            // just re-throw it so that it's managed by the retry policy
            throw e
        }
        catch (RegistryForwardException | RegistryUnauthorizedAccessException e) {
            // just re-throw it because it's a known error condition
            throw e
        }
        catch (Exception e) {
            throw new InternalServerException("Unexpected error on HTTP GET request '$uri'", e)
        }
    }

    HttpResponse<Void> head(String path, Map<String,List<String>> headers=null) {
        return head(makeUri(path), headers)
    }


    HttpResponse<Void> head(URI uri, Map<String, List<String>> headers) {
        final retryable = Retryable
                .<HttpResponse<Void>>of(httpConfig)
                .retryIf((resp) -> resp.statusCode() in retryableHttpErrors)
                .onRetry((event) -> "Failure on HEAD request: $uri - event: $event")
        // carry out the request
        return retryable.apply(()-> head0(uri,headers))
    }

    HttpResponse<Void> head0(URI uri, Map<String,List<String>> headers) {

        def result = head1(uri, headers)
        if( result.statusCode()==401 && registry.auth.isRefreshable() ) {
            // clear the token to force refreshing it
            loginService.invalidateAuthorization(image, registry.auth, credentials)
            result = head1(uri, headers)
        }
        return result
    }

    HttpResponse<Void> head1(URI uri, Map<String,List<String>> headers) {
        // A HEAD request is a GET request without a message body
        // https://zetcode.com/java/httpclient/
        final builder = HttpRequest.newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
        // copy headers 
        copyHeaders(headers, builder)
        // add authorisation header
        final header = loginService.getAuthorization(image, registry.auth, credentials)
        if( header )
            builder.setHeader("Authorization", header)
        // build the request
        final request = builder.build()
        // send it 
        final response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        traceResponse(response)
        return response
    }

    private void traceResponse(HttpResponse resp) {
        // dump response
        if( !log.isTraceEnabled() || !resp )
            return
        final trace = new StringBuilder()
        trace.append("= ${resp.request().method() ?: ''} [${resp.statusCode()}] ${resp.request().uri()}\n")
        trace.append('- request headers:\n')
        for( Map.Entry<String,List<String>> entry : resp.request().headers().map() ) {
            trace.append("> ${entry.key}=${entry.value?.join(',')}\n")
        }
        trace.append('- response headers:\n')
        for( Map.Entry<String,List<String>> entry : resp.headers().map() ) {
            trace.append("< ${entry.key}=${entry.value?.join(',')}\n")
        }
        log.trace(trace.toString())
    }

    Flux<ByteBuffer<?>> stream(ReactorStreamingHttpClient streamingHttpClient, String path, Map<String,List<String>> headers=null) {
        final uri = makeUri(path)
        final request = io.micronaut.http.HttpRequest.create(HttpMethod.GET, uri.toString())
        // copy headers
        copyHeaders(headers, request)
        // add authorisation header
        final header = loginService.getAuthorization(image, registry.auth, credentials)
        if( header )
            request.header("Authorization", header)

        return streamingHttpClient.dataStream(request)
    }

    List<String> curl(String path, Map<String,String> headers=null) {
        final result = new ArrayList(20)
        result.add('curl')
        result.add('-s')
        result.add('-X'); result.add('GET')
        //  copy headers
        for( Map.Entry<String,String> entry : headers )  {
            if( entry.key.toLowerCase() in SKIP_HEADERS )
                continue
            result.add('-H')
            result.add("${entry.key}: $entry.value")
        }
        // add authorisation header
        final header = loginService.getAuthorization(image, registry.auth, credentials)
        if( header ) {
            result.add('-H')
            result.add("Authorization: $header")
        }

        // the target URI
        result.add(makeUri(path).toString())
        return result
    }

}
