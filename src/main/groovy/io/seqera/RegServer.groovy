package io.seqera

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegServer {

    private HttpServer server

    private HttpHandler handler
    private int port = 9090
    private String path = '/'

    RegServer withPort(int port) {
        this.port = port
        return this
    }

    RegServer withPath(String path) {
        this.path = path
        return this
    }

    RegServer withHandler(HttpHandler handler) {
        this.handler = handler
        return this
    }

    RegServer start() {
        if( !handler )
            throw new IllegalArgumentException("Missing http handler")

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(path, handler);
        server.start()
        return this
    }

    void stop(int delay=0) {
        server?.stop(delay)
    }

}
