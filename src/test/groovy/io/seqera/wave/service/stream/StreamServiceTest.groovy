package io.seqera.wave.service.stream

import spock.lang.Specification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.util.ZipUtils
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class StreamServiceTest extends Specification{

    @Inject
    StreamService streamService

    def 'should stream data location' () {
        given:
        def data = "Hello world"
        def location = "data:" + new String(Base64.getEncoder().encode(data.bytes))
        when:
        def stream = streamService.stream(location)
        then:
        stream.text == data
    }

    def 'should stream gzip location' () {
        given:
        def data = "Hello world"
        def location = "gzip:" + new String(Base64.getEncoder().encode(ZipUtils.compress(data)))
        when:
        def stream = streamService.stream(location)
        then:
        stream.text == data
    }

    def 'should stream http location' () {
        given:
        def body = "Hello world!".bytes
        and:
        HttpHandler handler = { HttpExchange exchange ->
            exchange.getResponseHeaders().add("Content-Type", "application/text")
            exchange.sendResponseHeaders(200, body.size())
            exchange.getResponseBody() << body
            exchange.getResponseBody().close()
        }
        and:
        HttpServer server = HttpServer.create(new InetSocketAddress(9901), 0);
        server.createContext("/", handler);
        server.start()
        
        when:
        def stream = streamService.stream("http://localhost:9901/foo.txt")
        then:
        stream.text == 'Hello world!'

        cleanup:
        server?.stop(0)
    }

    def 'should stream docker location' () {
        given:
        def body = "Hello world!".bytes
        and:
        HttpHandler handler = { HttpExchange exchange ->
            exchange.getResponseHeaders().add("Content-Type", "application/tar+gzip")
            exchange.sendResponseHeaders(200, body.size())
            exchange.getResponseBody() << body
            exchange.getResponseBody().close()
        }
        and:
        HttpServer server = HttpServer.create(new InetSocketAddress(9901), 0);
        server.createContext("/", handler);
        server.start()

        when:
        def stream = streamService.stream("docker://localhost:9901/v2/library/ubuntu/blobs/content")
        then:
        stream.text == 'Hello world!'

        cleanup:
        server?.stop(0)
    }

}
