package io.seqera.controller

import groovy.util.logging.Slf4j
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.seqera.storage.Storage
import io.seqera.storage.DigestByteArray
import io.seqera.RouteHelper
import io.seqera.config.TowerConfiguration
import io.seqera.docker.ContainerService
import io.seqera.docker.ContainerService.DelegateResponse
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Controller("/v2")
class V2Controller {

    TowerConfiguration configuration
    ContainerService containerService
    Storage storage

    V2Controller(TowerConfiguration configuration, ContainerService containerService, Storage storage) {
        log.debug "Server configuration=$configuration"
        this.configuration = configuration
        this.containerService = containerService
        this.storage = storage
    }

    @Error
    HttpResponse<JsonError> handleError(HttpRequest request, Throwable t){
        log.error t.message, t
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

        def route = RouteHelper.parse("/v2/"+url, configuration.defaultRegistry.name)

        if( httpRequest.method == HttpMethod.HEAD )
            return handleHead(route, httpRequest)

        if (!(route.manifest || route.blob)) {
            return HttpResponse.notFound()
        }

        if( route.manifest ) {
            def entry = storage.getManifest(route.path)
            if (entry.present) {
                return fromCache(entry.get())
            }
        }

        if( route.blob){
            def entry = storage.getBlob(route.path)
            if (entry.present) {
                return fromCache(entry.get())
            }
        }

        def headers = httpRequest.headers.asMap() as Map<String, List<String>>
        def response = containerService.handleRequest(route, headers)
        fromDelegateResponse(response)
    }

    MutableHttpResponse<?> handleHead(RouteHelper.Route route, HttpRequest httpRequest) {

        if (!(route.manifest && route.tag)) {
            return HttpResponse.notFound()
        }

        def manifest = storage.getManifest(route.path)
        if (manifest.present) {
            return fromCache(manifest.get())
        }

        Map<String, List<String>> headers = httpRequest.headers.asMap() as Map<String, List<String>>
        def entry = containerService.handleManifest(route, headers)
        return fromCache(entry)
    }

    MutableHttpResponse<?> fromCache(DigestByteArray entry) {
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

    MutableHttpResponse<Flux<byte[]>>fromDelegateResponse(final DelegateResponse delegateResponse){

        Flux<byte[]> fluxInputStream = createFluxFromChunkBytes(delegateResponse.body)

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

    static Flux<byte[]> createFluxFromChunkBytes(InputStream inputStream){
        int size = 1024
        Flux.<byte[]>create({ emitter ->
            byte[]buff = new byte[size]
            int readed
            while( (readed=inputStream.read(buff, 0, size)) != -1){
                byte[] send = Arrays.copyOf(buff, readed)
                emitter.next(send)
            }
            emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER)
    }

}
