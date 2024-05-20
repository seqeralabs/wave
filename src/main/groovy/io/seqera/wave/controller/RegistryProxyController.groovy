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

package io.seqera.wave.controller

import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.io.buffer.ReferenceCounted
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpHeaders
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.ErrorHandler
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RegistryProxyService.DelegateResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.core.RoutePath
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.exchange.RegistryErrorResponse
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
import io.seqera.wave.service.blob.BlobCacheService
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.DockerDigestStore
import io.seqera.wave.storage.HttpDigestStore
import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.util.Retryable
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
/**
 * Implement a registry proxy controller that forward registry pull requests to the target service
 *
 * @author: jorge <jorge.aguilera@seqera.io>
 * @author: paolo di tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/v2")
@ExecuteOn(TaskExecutors.IO)
class RegistryProxyController {

    @Inject
    private HttpClientAddressResolver addressResolver

    @Inject
    private RegistryProxyService proxyService

    @Inject
    private Storage storage

    @Inject
    private RouteHandler routeHelper

    @Inject
    private ContainerBuildService containerBuildService

    @Inject
    @Nullable
    private RateLimiterService rateLimiterService

    @Inject
    private ErrorHandler errorHandler

    @Inject
    private HttpClientConfig httpConfig

    @Inject
    @Nullable
    private BlobCacheService blobCacheService

    @Value('${wave.cache.digestStore.maxWeightMb:350}')
    private int cacheMaxWeightMb

    @Error
    HttpResponse<RegistryErrorResponse> handleError(HttpRequest request, Throwable t) {
        return errorHandler.handle(request, t, (msg, code) -> new RegistryErrorResponse(code,msg) )
    }

    @Get
    Publisher<MutableHttpResponse<String>> index() {
        Mono.just(
                HttpResponse
                        .ok("OK")
                        .header("docker-distribution-api-version", "registry/2.0")
        )
    }

    @Get(uri="/{url:(.+)}", produces = "*/*")
    CompletableFuture<MutableHttpResponse<?>> handleGet(String url, HttpRequest httpRequest) {
        log.info "> Request [$httpRequest.method] $httpRequest.path"
        final route = routeHelper.parse("/v2/" + url)

        if( route.manifest && route.digest ){
            String ip = addressResolver.resolve(httpRequest)
            rateLimiterService?.acquirePull( new AcquireRequest(route.identity.userId as String, ip) )
        }

        // check if it's a container under build
        final future = handleFutureBuild0(route, httpRequest)
        if( future )
            return future
        else
            return CompletableFuture.completedFuture(handleGet0(route, httpRequest))
    }

    protected CompletableFuture<MutableHttpResponse<?>> handleFutureBuild0(RoutePath route, HttpRequest httpRequest){
        // check if there's a future build result
        final future = containerBuildService.buildResult(route)
        if( future ) {
            // wait for the build completion, then apply the usual 'handleGet0' logic
            future
                .thenApply( (build) -> build.exitStatus==0 ? handleGet0(route, httpRequest) : badRequest(build.logs) )
        }
        else
            return null
    }

    protected MutableHttpResponse<?> handleGet0(RoutePath route, HttpRequest httpRequest) {
        if( httpRequest.method == HttpMethod.HEAD )
            return handleHead(route, httpRequest)

        if (!(route.manifest || route.blob || route.tagList)) {
            throw new DockerRegistryException("Invalid request GET '$httpRequest.path'", 400, 'UNKNOWN')
        }

        if( route.manifest ) {
            if ( !route.digest ) {
                final entry = manifestForPath(route, httpRequest)
                if (entry) {
                    return fromCacheDigest(entry)
                }
            } else {
                final entry = storage.getManifest(route.getTargetPath())
                if (entry.present) {
                    return fromCacheDigest(entry.get())
                }
            }
        }

        if( route.blob ) {
            final entry = storage.getBlob(route.getTargetPath()).orElse(null)
            String location
            if( location=dockerRedirection(entry) ) {
                log.debug "Blob found in the cache: $route.path ==> mapping to: ${location}"
                final target = RoutePath.parse(location, route.identity)
                return handleDelegate0(target, httpRequest)
            }
            else if ( location=httpRedirect(entry) ) {
                log.debug "Blob found in the cache: $route.path  ==> mapping to: $location"
                return fromCacheRedirect(location)
            }
            else if( entry ) {
                return fromCacheDigest(entry)
            }
        }

        if( route.tagList ){
            log.debug "Handling tag list request '$route.path'"
            return handleTagList(route, httpRequest)
        }

        return handleDelegate0(route, httpRequest)
    }

    private String httpRedirect(DigestStore entry) {
        if( entry instanceof HttpDigestStore )
            return (entry as HttpDigestStore).location
        if( entry instanceof LazyDigestStore )
            return (entry as LazyDigestStore).location
        return null
    }

    private String dockerRedirection(DigestStore entry) {
        if( entry instanceof DockerDigestStore )
            return (entry as DockerDigestStore).location
        return null
    }

    protected MutableHttpResponse<?> handleDelegate0(RoutePath route, HttpRequest httpRequest) {
        final headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final resp = proxyService.handleRequest(route, headers)
        if( resp.isRedirect() ) {
            final loc = log.isTraceEnabled() ? resp.location : stripUriParams(resp.location)
            log.debug "Forwarding ${route.type} request '${route.getTargetContainer()}' to '${loc}'"
            return fromRedirectResponse(resp)
        }
        else if( resp.body!=null ) {
            log.debug "Returning ${route.type} from repository: '${route.getTargetContainer()}'"
            return fromContentResponse(resp, route)
        }
        else if( blobCacheService ) {
            log.debug "Forwarding ${route.type} cache request '${route.getTargetContainer()}'"
            return fromDownloadResponse(resp, route, headers)
        }
        else {
            log.debug "Pulling stream from repository: '${route.getTargetContainer()}'"
            return fromStreamResponse(resp, route, headers)
        }
    }

    protected DigestStore manifestForPath(RoutePath route, HttpRequest httpRequest) {
        // when the request contains a wave token and the manifest is specified
        // using a container 'tag' instead of a 'digest' the request path is used as storage key
        // because the target container path could be not unique (multiple wave containers request
        // could shared the same target container with a different configuration request)
        final unsolvedContainer = route.isUnresolvedManifest()
        final key = unsolvedContainer ? httpRequest.path : route.targetPath
        // check if there's cached manifest
        final manifest = storage.getManifest(key)
        if (manifest.present) {
            log.debug "Manifest cache hit ==> $key"
            return manifest.get()
        }
        // resolve the manifest using the proxy service
        Map<String, List<String>> headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final result = proxyService.handleManifest(route, headers)
        // cache the digest with the original route path to avoid to resolve one more time
        if( result && unsolvedContainer ) {
            storage.saveManifest(key, result)
        }
        return result
    }

    static protected MutableHttpResponse<?> badRequest(String message) {
        return HttpResponse.badRequest(new RegistryErrorResponse('UNKNOWN', message))
    }

    MutableHttpResponse<?> handleHead(RoutePath route, HttpRequest httpRequest) {

        if ( !route.manifest ) {
            throw new DockerRegistryException("Invalid request HEAD '$httpRequest.path'", 400, 'UNKNOWN')
        }

        final entry = manifestForPath(route, httpRequest)
        if( !entry ) {
            throw new DockerRegistryException("Unable to find cache manifest for '$httpRequest.path'", 400, 'UNKNOWN')
        }

        return fromCacheDigest(entry)
    }

    MutableHttpResponse<?> handleTagList(RoutePath route, HttpRequest httpRequest) {
        final headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final resp = proxyService.handleRequest(route, headers)
        HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .body(resp.body.bytes)
                .headers(toMutableHeaders(resp.headers))
    }

    private MutableHttpResponse fromCacheDigest(DigestStore entry) {
        final size = entry.getSize()
        final resp = entry.getBytes()

        Map<CharSequence, CharSequence> headers = Map.of(
                "Content-Length", String.valueOf(size),
                "Content-Type", entry.mediaType,
                "docker-content-digest", entry.digest,
                "etag", entry.digest,
                "docker-distribution-api-version", "registry/2.0") as Map<CharSequence, CharSequence>

        return HttpResponse
                .ok(resp)
                .headers(headers)
    }

    private MutableHttpResponse fromCacheRedirect(String location) {
        final override = Map.of(
                'Location', location,       // <-- the location can be relative to the origin host, override it to always return a fully qualified URI
                'Content-Length', '0',  // <-- make sure to set content length to zero, some services return some content even with the redirect header that's discarded by this response
                'Connection', 'close' ) // <-- make sure to return connection: close header otherwise docker hangs
        HttpResponse
                .status(HttpStatus.TEMPORARY_REDIRECT)
                .headers(toMutableHeaders(Map.of(), override))
    }

    MutableHttpResponse<?> fromRedirectResponse(final DelegateResponse resp) {
        final override = Map.of(
                    'Location', resp.location,  // <-- the location can be relative to the origin host, override it to always return a fully qualified URI
                    'Content-Length', '0',  // <-- make sure to set content length to zero, some services return some content even with the redirect header that's discarded by this response
                    'Connection', 'close' ) // <-- make sure to return connection: close header otherwise docker hangs
        return HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .headers(toMutableHeaders(resp.headers, override))
    }

    MutableHttpResponse<?> fromDownloadResponse(final DelegateResponse resp, RoutePath route, Map<String, List<String>> headers) {
        log.debug "== Blob cache upastream $resp"
        final blobCache = blobCacheService .retrieveBlobCache(route, headers, resp.headers)
        log.debug "== Blob cache response [succeeded=${blobCache.succeeded()}] $blobCache"
        if( !blobCache.succeeded() ) {
            final String msg = blobCache.logs ?: "Unable to cache blob ${blobCache.locationUri}"
            return badRequest(msg)
        }

        final override = Map.of(
                'Location', blobCache.locationUri,   // <-- the location can be relative to the origin host, override it to always return a fully qualified URI
                'Content-Length', '0',      // <-- make sure to set content length to zero, some services return some content even with the redirect header that's discarded by this response
                'Connection', 'close' )     // <-- make sure to return connection: close header otherwise docker hangs

        HttpResponse
                .status(HttpStatus.TEMPORARY_REDIRECT)
                .headers(toMutableHeaders(resp.headers, override))
    }

    MutableHttpResponse<?> fromStreamResponse(final DelegateResponse response, RoutePath route, Map<String,List<String>> headers){

        final stream = proxyService.streamBlob(route, headers)
                .doOnNext(byteBuffer -> {
                    if (byteBuffer instanceof ReferenceCounted) {
                        byteBuffer.retain()
                    }
                })

        HttpResponse
                .status(HttpStatus.valueOf(response.statusCode))
                .body(stream)
                .headers(toMutableHeaders(response.headers))
    }

    MutableHttpResponse<?> fromContentResponse(DelegateResponse resp, RoutePath route) {
        // create the retry logic on error                                                              §
        final retryable = Retryable
                .<byte[]>of(httpConfig)
                .onRetry((event) -> log.warn("Unable to read manifest body - request: $route; event: $event"))

        HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .body(retryable.apply(()-> resp.body.bytes))
                .headers(toMutableHeaders(resp.headers))
    }

    static protected Consumer<MutableHttpHeaders> toMutableHeaders(Map<String,List<String>> headers, Map<String,String> override=Collections.emptyMap()) {
        new Consumer<MutableHttpHeaders>() {
            @Override
            void accept(MutableHttpHeaders mutableHttpHeaders) {
                for( Map.Entry<String,List<String>> entry : headers ) {
                    for( String value : entry.value )
                        mutableHttpHeaders.add(entry.key, value)
                }
                // override headers with specified value
                for( Map.Entry<String,String> entry : override ) {
                    mutableHttpHeaders.putAt(entry.key, entry.value)
                }
            }
        }
    }


    static String stripUriParams(String uri) {
        final p = uri.indexOf('?')
        return p==-1 ? uri : uri.substring(0,p) + '?PARAMS+OMITTED'
    }
}
