package io.seqera.controller

import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files

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

    ContainerService containerService
    Storage storage

    V2Controller(ContainerService containerService, Storage storage) {
        this.containerService = containerService
        this.storage = storage
    }

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
        def route = RouteHelper.parse("/v2/"+url)

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

    protected DigestByteArray manifestForPath(RouteHelper.Route route, HttpRequest httpRequest) {
        def manifest = storage.getManifest(route.path)
        if (manifest.present) {
            return manifest.get()
        }

        Map<String, List<String>> headers = httpRequest.headers.asMap() as Map<String, List<String>>
        return containerService.handleManifest(route, headers)
    }

    MutableHttpResponse<?> handleHead(RouteHelper.Route route, HttpRequest httpRequest) {

        if (!(route.manifest && route.tag)) {
            return HttpResponse.notFound()
        }

        final entry = manifestForPath(route, httpRequest)
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

    MutableHttpResponse<Flux<ByteBuffer>>fromDelegateResponse(final DelegateResponse delegateResponse){

        final fluxInputStream = createFluxFromChunkBytes(delegateResponse.body)

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

    static Flux<ByteBuffer> createFluxFromChunkBytes(InputStream inputStream){
        int size = 32 * 1024
        Flux.<ByteBuffer>create({ emitter ->
            def file = Files.createTempFile('image','download').toFile()
            def rnd = new RandomAccessFile(file, "rw")
            def ch = rnd.getChannel()
            byte[]buff = new byte[size]
            int readed
            int index=0
            while( (readed=inputStream.read(buff, 0, size)) != -1){
                // save to the rnd
                ch.write(ByteBuffer.wrap(buff, 0, readed) )
                // read mapped by buffer from the rnd
                MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, size * index++, readed)
                emitter.next(buf)
            }
            emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER)
    }

}
