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

package io.seqera.wave.controller

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import io.micronaut.core.annotation.Nullable

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.Weigher
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpHeaders
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.StreamedFile
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
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.storage.DigestKey
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.TimedInputStream
import jakarta.annotation.PostConstruct
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

    @Inject HttpClientAddressResolver addressResolver
    @Inject RegistryProxyService proxyService
    @Inject Storage storage
    @Inject RouteHandler routeHelper
    @Inject ContainerBuildService containerBuildService
    @Inject @Nullable RateLimiterService rateLimiterService
    @Inject ErrorHandler errorHandler

    @Inject MeterRegistry meterRegistry
    @Inject HttpClientConfig httpConfig

    @Value('${wave.cache.digestStore.maxWeightMb:350}')
    int cacheMaxWeightMb

    private static final int _1MB = 1024*1024

    private LoadingCache<DigestKey,byte[]> digestsCache

    @PostConstruct
    private void init() {
        log.debug "Creating proxy controller - cacheMaxWeightMb=$cacheMaxWeightMb"
        // initialize the cache builder
        digestsCache = CacheBuilder
                .newBuilder()
                .maximumWeight(cacheMaxWeightMb  * _1MB)
                .weigher(new Weigher<DigestKey, byte[]>() {
                    @Override
                    int weigh(DigestKey key, byte[] value) {
                        return value.length
                    }
                })
                .build(digestKeyCacheLoader())
    }

    private CacheLoader<DigestKey, byte[]> digestKeyCacheLoader() {
        // create the retry logic on error
        final retryable = Retryable
                .<byte[]>of(httpConfig)
                .onRetry((event) -> log.warn("Unable to load digest-store - event: $event"))
        // load all bytes, this can invoke a remote http request
        new CacheLoader<DigestKey, byte[]>() {
            @Override
            byte[] load(DigestKey key) throws Exception {
                log.debug("Loading digest-store=${key}")
                return retryable.apply(() -> key.readAllBytes())
            }
        }
    }

    @Error
    HttpResponse<RegistryErrorResponse> handleError(HttpRequest request, Throwable t) {
        return errorHandler.handle(request, t, (msg, code) -> new RegistryErrorResponse(msg,code) )
    }

    @Get
    Publisher<MutableHttpResponse<String>> index() {
        Mono.just(
                HttpResponse
                        .ok("OK")
                        .header("docker-distribution-api-version", "registry/2.0")
        )
    }

    protected void increaseWavePullsCounter(RoutePath route) {
        try {
            final tags = new ArrayList<String>(20)
            tags.add('registry'); tags.add(route.registry)
            tags.add('repository'); tags.add(route.repository)
            tags.add('container'); tags.add(route.targetContainer)
            if( route.request?.towerEndpoint ) {
                tags.add('endpoint'); tags.add(route.request.towerEndpoint)
            }
            if( route.request?.userId ) {
                tags.add('userId'); tags.add(route.request.userId as String)
            }
            if( route.request?.workspaceId ) {
                tags.add('workspaceId'); tags.add(route.request.workspaceId as String)
            }

            meterRegistry.counter('wave.pulls', tags as String[]).increment()
        }
        catch (Throwable e) {
            log.error "Unable to increment wave pulls counter", e
        }
    }

    protected void increaseFusionPullsCounter(RoutePath route) {
        try {
            final version = route.request?.containerConfig?.fusionVersion()
            if( version ) {
                meterRegistry.counter('fusion.pulls', 'version', version.number, 'arch', version.arch).increment()
            }
        }
        catch (Throwable e) {
            log.error "Unable to increment fusion pulls counter", e
        }
    }

    @Get(uri="/{url:(.+)}", produces = "*/*")
    CompletableFuture<MutableHttpResponse<?>> handleGet(String url, HttpRequest httpRequest) {
        log.info "> Request [$httpRequest.method] $httpRequest.path"
        final route = routeHelper.parse("/v2/" + url)

        if( route.manifest && route.digest ){
            String ip = addressResolver.resolve(httpRequest)
            rateLimiterService?.acquirePull( new AcquireRequest(route.request?.userId?.toString(), ip) )
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
                    return fromCache(entry)
                }
            } else {
                final entry = storage.getManifest(route.getTargetPath())
                if (entry.present) {
                    return fromCache(entry.get())
                }
            }
        }

        if( route.blob ) {
            final entry = storage.getBlob(route.getTargetPath())
            if (entry.present) {
                log.info "Blob found in the cache: $route.path"
                return fromCache(entry.get())
            }
        }

        if( route.tagList ){
            log.debug "Handling tag list request '$route.path'"
            return handleTagList(route, httpRequest)
        }

        final headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final resp = proxyService.handleRequest(route, headers)
        if( resp.isRedirect() ) {
            final loc = log.isTraceEnabled() ? resp.location : stripUriParams(resp.location)
            log.debug "Forwarding ${route.type} request '${route.getTargetContainer()}' to '${loc}'"
            return fromRedirectResponse(resp)
        }
        else if( route.isManifest() ) {
            log.debug "Pulling manifest from repository: '${route.getTargetContainer()}'"
            return fromManifestResponse(resp)
        }
        else {
            log.debug "Pulling blob from repository: '${route.getTargetContainer()}'"
            return fromDelegateResponse(resp)
        }
    }

    protected DigestStore manifestForPath(RoutePath route, HttpRequest httpRequest) {
        // increase the quest counters
        increaseWavePullsCounter(route)
        increaseFusionPullsCounter(route)
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
        return HttpResponse.badRequest(new RegistryErrorResponse(message))
    }

    MutableHttpResponse<?> handleHead(RoutePath route, HttpRequest httpRequest) {

        if ( !route.manifest ) {
            throw new DockerRegistryException("Invalid request HEAD '$httpRequest.path'", 400, 'UNKNOWN')
        }

        final entry = manifestForPath(route, httpRequest)
        if( !entry ) {
            throw new DockerRegistryException("Unable to find cache manifest for '$httpRequest.path'", 400, 'UNKNOWN')
        }

        return fromCache(entry)
    }

    MutableHttpResponse<?> handleTagList(RoutePath route, HttpRequest httpRequest) {
        final headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final resp = proxyService.handleRequest(route, headers)
        HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .body(resp.body.bytes)
                .headers(toMutableHeaders(resp.headers))
    }

    MutableHttpResponse<?> fromCache(DigestStore entry) {

        final size = entry.size
        final resp = digestsCache.get( DigestKey.of(entry) )

        Map<CharSequence, CharSequence> headers = Map.of(
                        "Content-Length", String.valueOf(size),
                        "Content-Type", entry.mediaType,
                        "docker-content-digest", entry.digest,
                        "etag", entry.digest,
                        "docker-distribution-api-version", "registry/2.0") as Map<CharSequence, CharSequence>

        HttpResponse
                .ok(resp)
                .headers(headers)
    }

    MutableHttpResponse<?> fromRedirectResponse(final DelegateResponse resp) {
        final override = Map.of(
                    'Location', resp.location,  // <-- the location can be relative to the origin host, override it to always return a fully qualified URI
                    'Connection', 'close' ) // <-- make sure to return connection: close header otherwise docker hangs
        return HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .headers(toMutableHeaders(resp.headers, override))
    }

    MutableHttpResponse<?> fromDelegateResponse(final DelegateResponse response){

        final Long len = response.headers
                .find {it.key.toLowerCase()=='content-length'}?.value?.first() as Long ?: null

        final wrap = new TimedInputStream(response.body, httpConfig.getReadTimeout())
        final streamedFile =  len
                ?  new StreamedFile(wrap, MediaType.APPLICATION_OCTET_STREAM_TYPE, Instant.now().toEpochMilli(), len)
                :  new StreamedFile(wrap, MediaType.APPLICATION_OCTET_STREAM_TYPE)

        HttpResponse
                .status(HttpStatus.valueOf(response.statusCode))
                .body(streamedFile)
                .headers(toMutableHeaders(response.headers))
    }

    MutableHttpResponse<?> fromManifestResponse(DelegateResponse resp) {
        HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .body(resp.body.bytes)
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
