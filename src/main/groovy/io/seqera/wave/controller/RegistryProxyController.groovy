package io.seqera.wave.controller

import java.time.Instant
import java.util.concurrent.CompletableFuture

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
import io.seqera.wave.ErrorHandler
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RegistryProxyService.DelegateResponse
import io.seqera.wave.core.RouteHelper
import io.seqera.wave.core.RoutePath
import io.seqera.wave.exception.GenericException
import io.seqera.wave.exchange.RegistryErrorResponse
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

    @Inject RegistryProxyService proxyService
    @Inject Storage storage
    @Inject RouteHelper routeHelper
    @Inject ContainerBuildService containerBuildService
    @Inject ErrorHandler errorHandler

    @Error
    HttpResponse<RegistryErrorResponse> handleError(HttpRequest request, Throwable t) {
        final String details = t instanceof GenericException ? t.details : null
        return errorHandler.handle(request, t, (msg, id) -> new RegistryErrorResponse(msg,id,details) )
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

        if (!(route.manifest || route.blob)) {
            return notFound()
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

        final type = route.isManifest() ? 'manifest' : 'blob'
        log.debug "Pulling $type from remote host: $route.path"
        def headers = httpRequest.headers.asMap() as Map<String, List<String>>
        def response = proxyService.handleRequest(route, headers)
        fromDelegateResponse(response)
    }

    protected DigestStore manifestForPath(RoutePath route, HttpRequest httpRequest) {
        def manifest = storage.getManifest(route.path)
        if (manifest.present) {
            return manifest.get()
        }

        Map<String, List<String>> headers = httpRequest.headers.asMap() as Map<String, List<String>>
        return proxyService.handleManifest(route, headers)
    }

    static protected MutableHttpResponse<?> notFound(){
        return HttpResponse.notFound()
    }

    static protected MutableHttpResponse<?> badRequest(String message) {
        return HttpResponse.badRequest(new RegistryErrorResponse(message))
    }

    MutableHttpResponse<?> handleHead(RoutePath route, HttpRequest httpRequest) {

        if (!(route.manifest && route.tag)) {
            return notFound()
        }

        final entry = manifestForPath(route, httpRequest)
        if( !entry ) {
            log.warn "Unable to find cache manifest for $route"
            return notFound()
        }
        return fromCache(entry)
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

    MutableHttpResponse<?>fromDelegateResponse(final DelegateResponse delegateResponse){

        final Long contentLength = delegateResponse.headers
                .find {it.key.toLowerCase()=='content-length'}?.value?.first() as long ?: null
        final fluxInputStream = createFluxFromChunkBytes(delegateResponse.body, contentLength)

        HttpResponse
                .status(HttpStatus.valueOf(delegateResponse.statusCode))
                .body( fluxInputStream )
                .headers({MutableHttpHeaders mutableHttpHeaders ->
                    delegateResponse.headers.each {entry->
                        entry.value.each{ value ->
                            mutableHttpHeaders.add(entry.key, value)
                        }
                    }
                })
    }

    static StreamedFile createFluxFromChunkBytes(InputStream inputStream, Long size){
        if( size )
            new StreamedFile(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE, Instant.now().toEpochMilli(), size)
        else
            new StreamedFile(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE)
    }

}
