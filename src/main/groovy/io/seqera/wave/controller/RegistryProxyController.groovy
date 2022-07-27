package io.seqera.wave.controller

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.http.server.types.files.StreamedFile
import io.seqera.wave.core.RoutePath
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.storage.Storage
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.core.RouteHelper
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RegistryProxyService.DelegateResponse
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * Implement a registry proxy controller that forward registry pull requests to the target service
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 */
@Slf4j
@CompileStatic
@Controller("/v2")
class RegistryProxyController {

    @Inject RegistryProxyService proxyService
    @Inject Storage storage
    @Inject RouteHelper routeHelper
    @Inject ContainerBuildService containerBuildService

    @Error
    HttpResponse<JsonError> handleError(HttpRequest request, Throwable t){
        log.info t.message, t
        JsonError error = new JsonError("Error: " + t.message).link(Link.SELF, Link.of(request.getUri()))
        HttpResponse.<JsonError>serverError().body(error)
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
    MutableHttpResponse<?> handleGet(String url, HttpRequest httpRequest) {
        log.info "> Request [$httpRequest.method] $httpRequest.path"
        final route = routeHelper.parse("/v2/"+url)

        // check if it's a container under build
        final targetImage = route.request?.containerImage
        final status = containerBuildService.waitImageBuild(targetImage)

        if( httpRequest.method == HttpMethod.HEAD )
            return handleHead(route, httpRequest)

        if (!(route.manifest || route.blob)) {
            return HttpResponse.notFound()
        }

        if (route.manifest && !route.digest) {
            def entry = manifestForPath(route, httpRequest)
            if (entry) {
                return fromCache(entry)
            }
        }
        else if( route.manifest ) {
            def entry = storage.getManifest(route.path)
            if (entry.present) {
                return fromCache(entry.get())
            }
        }

        if( route.blob ) {
            def entry = storage.getBlob(route.path)
            if (entry.present) {
                log.info "Blob found in the cache: $route.path"
                return fromCache(entry.get())
            }
        }

        log.debug "Blob pulling from remote host: $route.path"
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

    MutableHttpResponse<?> handleHead(RoutePath route, HttpRequest httpRequest) {

        if (!(route.manifest && route.tag)) {
            return HttpResponse.notFound()
        }

        final entry = manifestForPath(route, httpRequest)
        if( !entry ) {
            log.warn "Unable to find cache manifest for $route"
            return HttpResponse.notFound()
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

    MutableHttpResponse<StreamedFile>fromDelegateResponse(final DelegateResponse delegateResponse){

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
