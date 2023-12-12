/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.exceptions.InternalServerException
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.auth.RegistryUnauthorizedAccessException
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.util.RegHelper
import io.seqera.wave.util.Retryable
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

    ProxyClient(HttpClient httpClient, HttpClientConfig httpConfig) {
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

    private URI makeUri(String path) {
        assert path.startsWith('/'), "Request past should start with a slash character — offending path: $path"
        URI.create(registry.host.toString() + path)
    }

    HttpResponse<String> getString(String path, Map<String,List<String>> headers=null, boolean followRedirect=true) {
        try {
            return get( makeUri(path), headers, String.class, followRedirect )
        }
        catch (ClientResponseException e) {
            return ErrResponse<String>.forString(e.message, e.request)
        }
    }

    HttpResponse<InputStream> getStream(String path, Map<String,List<String>> headers=null, boolean followRedirect=true) {
        try {
            return get( makeUri(path), headers, InputStream.class, followRedirect )
        }
        catch (ClientResponseException e) {
            return ErrResponse.forStream(e.message, e.request)
        }
    }

    HttpResponse<byte[]> getBytes(String path, Map<String,List<String>> headers=null, boolean followRedirect=true) {
        try {
            return get( makeUri(path), headers, byte[].class, followRedirect )
        }
        catch (ClientResponseException e) {
            return ErrResponse.forByteArray(e.message, e.request)
        }
    }


    private static final List<String> SKIP_HEADERS = ['host', 'connection', 'authorization', 'content-length']

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

    def <T> HttpResponse<T> get(URI origin, Map<String,List<String>> headers, Class<T> bodyType, boolean followRedirect) {
        final retryable = Retryable
                .<HttpResponse<T>>of(httpConfig)
                .retryIf((resp) -> resp.code() in HTTP_RETRYABLE_ERRORS)
                .onRetry((event) -> "Failure on GET request: $origin - event: $event")
        // carry out the request
        return retryable.apply(()-> get0(origin, headers, bodyType, followRedirect))
    }

    def <T> HttpResponse<T> get0(URI origin, Map<String,List<String>> headers, Class<T> bodyType, boolean followRedirect) {
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
            final request = HttpRequest.create(HttpMethod.GET, target.toString())
            final result = get1(target, headers, bodyType, authorize, request)
            // add to visited URL
            visited.add(target)
            // check the result code
            if( result.code()==401 && (authRetry++)<2 && host==target.host && registry.auth.isRefreshable() ) {
                // clear the token to force refreshing it
                loginService.invalidateAuthorization(image, registry.auth, credentials)
                continue
            }
            if( result.code() in HTTP_REDIRECT_CODES && followRedirect ) {
                final redirect = result.header('location')
                log.trace "Redirecting (${++redirectCount}) $target ==> $redirect ${RegHelper.dumpHeaders(result.headers.asMap())}"
                if( !redirect ) {
                    final msg = "Missing `Location` header for request URI '$target' ― origin request '$origin'"
                    throw new ClientResponseException(msg, request)
                }
                // the redirect location can be a relative path i.e. without hostname
                // therefore resolve it against the target registry hostname
                target = registry.host.resolve(redirect)
                if( target in visited ) {
                    final msg = "Redirect location already visited: $redirect ― origin request '$origin'"
                    throw new ClientResponseException(msg, request)
                }
                if( visited.size()>=10 ) {
                    final msg = "Redirect location already visited: $redirect ― origin request '$origin'"
                    throw new ClientResponseException(msg, request)
                }
                // go head with with the redirection
                continue
            }
            return result
        }

    }

    private <T> HttpResponse<T> get1(URI uri, Map<String,List<String>> headers, Class<T> bodyType, boolean authorize, MutableHttpRequest request) {
        try{
            copyHeaders(headers, request)
            if( authorize ) {
                // add authorisation header
                final header = loginService.getAuthorization(image, registry.auth, credentials)
                if( header )
                    request.header("Authorization", header)
            }
            // send it
            HttpResponse<T> response = httpClient.toBlocking().exchange(request, bodyType)
            traceResponse(request, response)
            return response
        }
        catch (IOException e) {
            // just re-throw it so that it's managed by the retry policy
            throw e
        }
        catch (RegistryUnauthorizedAccessException e) {
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

    HttpResponse<Void> head(URI uri, Map<String,List<String>> headers) {
        final retryable = Retryable
                .<HttpResponse<Void>>of(httpConfig)
                .retryIf((resp) -> resp.code() in HTTP_RETRYABLE_ERRORS)
                .onRetry((event) -> "Failure on HEAD request: $uri - event: $event")
        // carry out the request
        return retryable.apply(()-> head0(uri,headers))
    }

    HttpResponse<Void> head0(URI uri, Map<String,List<String>> headers) {

        def result = head1(uri, headers)
        if( result.code()==401 && registry.auth.isRefreshable() ) {
            // clear the token to force refreshing it
            loginService.invalidateAuthorization(image, registry.auth, credentials)
            result = head1(uri, headers)
        }
        return result
    }

    HttpResponse<Void> head1(URI uri, Map<String,List<String>> headers) {
        // A HEAD request is a GET request without a message body
        // https://zetcode.com/java/httpclient/
        final request = HttpRequest.create(HttpMethod.HEAD, uri.toString())
        // copy headers 
        copyHeaders(headers, request)
        // add authorisation header
        final header = loginService.getAuthorization(image, registry.auth, credentials)
        if( header )
            request.header("Authorization", header)
        // send it
        final response= httpClient.toBlocking().exchange(request, Void)
        traceResponse(request, response)
        return response
    }

    private void traceResponse(HttpRequest request, HttpResponse resp) {
        // dump response
        if( !log.isTraceEnabled() || !resp )
            return
        final trace = new StringBuilder()
        trace.append("= ${request.getMethodName() ?: ''} [${resp.code()}] ${request.getUri()}\n")
        trace.append('- request headers:\n')
        for( Map.Entry<String,List<String>> entry : request.headers.asMap() ) {
            trace.append("> ${entry.key}=${entry.value?.join(',')}\n")
        }
        trace.append('- response headers:\n')
        for( Map.Entry<String,List<String>> entry : resp.headers.asMap() ) {
            trace.append("< ${entry.key}=${entry.value?.join(',')}\n")
        }
        log.trace(trace.toString())
    }
}
