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

package io.seqera.wave.service.stream

import spock.lang.Specification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.test.TestHelper
import io.seqera.wave.tower.PlatformId
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
        def stream = streamService.stream(location, Mock(PlatformId))
        then:
        stream.text == data
    }

    def 'should stream gzip location' () {
        given:
        def data = "Hello world"
        def location = "gzip:" + new String(Base64.getEncoder().encode(ZipUtils.compress(data)))
        when:
        def stream = streamService.stream(location, Mock(PlatformId))
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
        def stream = streamService.stream("http://localhost:9901/foo.txt", Mock(PlatformId))
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
        def stream = streamService.stream("docker://localhost:9901/v2/library/ubuntu/blobs/content", Mock(PlatformId))
        then:
        stream.text == 'Hello world!'

        cleanup:
        server?.stop(0)
    }

    def 'should stream large payload using flux bytebuffer' () {
        given:
        def body = TestHelper.generateRandomString(5 * 1024 * 1025).bytes
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
        def stream = streamService.stream("docker://localhost:9901/v2/library/ubuntu/blobs/content", Mock(PlatformId))
        then:
        stream.readAllBytes() == body

        cleanup:
        server?.stop(0)
    }

}
