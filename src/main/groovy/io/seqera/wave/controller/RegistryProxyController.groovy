package io.seqera.wave.controller

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
import io.seqera.wave.ErrorHandler
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RegistryProxyService.DelegateResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.core.RoutePath
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.exchange.RegistryErrorResponse
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
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
class RegistryProxyController {

    @Inject HttpClientAddressResolver addressResolver
    @Inject RegistryProxyService proxyService
    @Inject Storage storage
    @Inject RouteHandler routeHelper
    @Inject ContainerBuildService containerBuildService
    @Inject @Nullable RateLimiterService rateLimiterService
    @Inject ErrorHandler errorHandler

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
                .thenApply( (build) -> build.exitStatus==0 ? handleGet0(route, httpRequest) : badRequest(build.logs ) )
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
                def entry = manifestForPath(route, httpRequest)
                if (entry) {
                    return fromCache(entry)
                }
            } else {
                def entry = storage.getManifest(route.path)
                if (entry.present) {
                    return fromCache(entry.get())
                }
            }
        }

        if( route.blob ) {
            def entry = storage.getBlob(route.path)
            if (entry.present) {
                log.info "Blob found in the cache: $route.path"
                return fromCache(entry.get())
            }
        }

        if( route.tagList ){
            return handleTagList(route, httpRequest)
        }

        final type = route.isManifest() ? 'manifest' : 'blob'
        final headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final resp = proxyService.handleRequest(route, headers)
        if( resp.isRedirect() ) {
            log.debug "Redirecting $type request '$route.path' to '$resp.location'"
            return fromRedirectResponse(resp)
        }
        else {
            log.debug "Pulling $type from remote host: '$route.path'"
            return fromDelegateResponse(resp)
        }
    }

    protected DigestStore manifestForPath(RoutePath route, HttpRequest httpRequest) {
        def manifest = storage.getManifest(route.path)
        if (manifest.present) {
            return manifest.get()
        }

        Map<String, List<String>> headers = httpRequest.headers.asMap() as Map<String, List<String>>
        return proxyService.handleManifest(route, headers)
    }

    static protected MutableHttpResponse<?> badRequest(String message) {
        return HttpResponse.badRequest(new RegistryErrorResponse(message))
    }

    MutableHttpResponse<?> handleHead(RoutePath route, HttpRequest httpRequest) {

        if (!(route.manifest && route.tag)) {
            throw new DockerRegistryException("Invalid request HEAD '$httpRequest.path'", 400, 'UNKNOWN')
        }

        final entry = manifestForPath(route, httpRequest)
        if( !entry ) {
            throw new DockerRegistryException("Unable to find cache manifest for '$httpRequest.path'", 400, 'UNKNOWN')
        }
        return fromCache(entry)
    }

    MutableHttpResponse<?> handleTagList(RoutePath route, HttpRequest httpRequest) {
        log.debug "Handling tag list request '$route.path'"
        final headers = httpRequest.headers.asMap() as Map<String, List<String>>
        final resp = proxyService.handleRequest(route, headers)
        HttpResponse
                .status(HttpStatus.valueOf(resp.statusCode))
                .body(resp.body.bytes)
                .headers(toMutableHeaders(resp.headers))
    }

    MutableHttpResponse<?> fromCache(DigestStore entry) {
        Map<CharSequence, CharSequence> headers = Map.of(
                        "Content-Length", entry.bytes.length.toString(),
                        "Content-Type", entry.mediaType,
                        "docker-content-digest", entry.digest,
                        "etag", entry.digest,
                        "docker-distribution-api-version", "registry/2.0") as Map<CharSequence, CharSequence>
        MutableHttpResponse
                .ok( entry.bytes )
                .headers(headers)
    }

    MutableHttpResponse<?>fromRedirectResponse(final DelegateResponse delegateResponse) {
        HttpResponse
                .status(HttpStatus.valueOf(delegateResponse.statusCode))
                .headers(toMutableHeaders(delegateResponse.headers, [Connection: 'close'])) // <-- make sure to return connection: close header otherwise docker hangs
    }

    MutableHttpResponse<?>fromDelegateResponse(final DelegateResponse delegateResponse){

        final Long contentLength = delegateResponse.headers
                .find {it.key.toLowerCase()=='content-length'}?.value?.first() as long ?: null
        final fluxInputStream = createFluxFromChunkBytes(delegateResponse.body, contentLength)

        HttpResponse
                .status(HttpStatus.valueOf(delegateResponse.statusCode))
                .body(fluxInputStream)
                .headers(toMutableHeaders(delegateResponse.headers))
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
                    mutableHttpHeaders.add(entry.key, entry.value)
                }
            }
        }
    }

    static protected StreamedFile createFluxFromChunkBytes(InputStream inputStream, Long size){
        if( size )
            new StreamedFile(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE, Instant.now().toEpochMilli(), size)
        else
            new StreamedFile(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE)
    }

}
