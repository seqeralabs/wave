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

package io.seqera.wave.http

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
/**
 * Minimal HTTP forward proxy supporting Basic proxy authentication, plain HTTP
 * forwarding and HTTPS {@code CONNECT} tunnelling - for testing purposes only
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class FakeProxyServer implements Closeable {

    final String username
    final String password

    final AtomicInteger authorized = new AtomicInteger()
    final AtomicInteger rejected = new AtomicInteger()
    final List<String> connectRequests = new CopyOnWriteArrayList<>()

    private final ServerSocket server
    private final String expectedAuthorization
    private volatile boolean closed

    FakeProxyServer(String username=null, String password=null) {
        this.username = username
        this.password = password
        this.expectedAuthorization = username
                ? 'Basic ' + Base64.getEncoder().encodeToString("$username:$password".toString().getBytes('ISO-8859-1'))
                : null
        this.server = new ServerSocket(0, 50, InetAddress.getByName('127.0.0.1'))
        Thread.startDaemon("FakeProxyServer-acceptor") {
            while( !closed ) {
                try {
                    final socket = server.accept()
                    Thread.startDaemon("FakeProxyServer-worker") { handle(socket) }
                }
                catch (IOException e) {
                    if( !closed )
                        e.printStackTrace()
                }
            }
        }
    }

    int getPort() {
        return server.localPort
    }

    @Override
    void close() {
        closed = true
        server.close()
    }

    private void handle(Socket socket) {
        try {
            socket.soTimeout = 15_000
            final input = new BufferedInputStream(socket.inputStream)
            final output = socket.outputStream
            while( true ) {
                final head = readHead(input)
                if( !head )
                    break
                final lines = head.split('\r\n') as List<String>
                final request = lines[0]
                final headers = new HashMap<String,String>()
                for( String line : lines.drop(1) ) {
                    final p = line.indexOf(':')
                    if( p > 0 )
                        headers.put(line.substring(0, p).trim().toLowerCase(), line.substring(p + 1).trim())
                }
                if( username && headers.get('proxy-authorization') != expectedAuthorization ) {
                    rejected.incrementAndGet()
                    output.write('HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm="test"\r\nContent-Length: 0\r\n\r\n'.getBytes('ISO-8859-1'))
                    output.flush()
                    // keep the connection open - the client may retry with credentials
                    continue
                }
                authorized.incrementAndGet()
                if( request.startsWith('CONNECT ') ) {
                    connectRequests.add(request)
                    tunnel(request, input, output)
                }
                else {
                    forward(request, headers, output)
                }
                break
            }
        }
        catch (Exception e) {
            // ignore - connection closed
        }
        finally {
            try { socket.close() } catch (IOException e) { }
        }
    }

    private static void tunnel(String request, InputStream input, OutputStream output) {
        final target = request.tokenize(' ')[1]
        final p = target.lastIndexOf(':')
        final upstream = new Socket(target.substring(0, p), target.substring(p + 1) as int)
        try {
            output.write('HTTP/1.1 200 Connection established\r\n\r\n'.getBytes('ISO-8859-1'))
            output.flush()
            Thread.startDaemon("FakeProxyServer-tunnel") { pump(upstream.inputStream, output) }
            pump(input, upstream.outputStream)
        }
        finally {
            try { upstream.close() } catch (IOException e) { }
        }
    }

    private static void forward(String request, Map<String,String> headers, OutputStream output) {
        final parts = request.tokenize(' ')
        final uri = new URI(parts[1])
        final port = uri.port > 0 ? uri.port : 80
        final path = (uri.rawPath ?: '/') + (uri.rawQuery ? '?' + uri.rawQuery : '')
        new Socket(uri.host, port).withCloseable { upstream ->
            final writer = upstream.outputStream
            writer.write("${parts[0]} ${path} HTTP/1.1\r\n".toString().getBytes('ISO-8859-1'))
            writer.write("Host: ${uri.host}:${port}\r\n".toString().getBytes('ISO-8859-1'))
            writer.write('Connection: close\r\n'.getBytes('ISO-8859-1'))
            for( Map.Entry<String,String> entry : headers ) {
                if( entry.key in ['host', 'connection', 'proxy-authorization', 'proxy-connection'] )
                    continue
                writer.write("${entry.key}: ${entry.value}\r\n".toString().getBytes('ISO-8859-1'))
            }
            writer.write('\r\n'.getBytes('ISO-8859-1'))
            writer.flush()
            pump(upstream.inputStream, output)
        }
    }

    private static String readHead(InputStream input) {
        final buffer = new ByteArrayOutputStream()
        int window = 0
        int ch
        while( (ch = input.read()) != -1 ) {
            buffer.write(ch)
            window = (window << 8) | (ch & 0xff)
            if( window == 0x0d0a0d0a )
                return new String(buffer.toByteArray(), 0, buffer.size() - 4, 'ISO-8859-1')
        }
        return null
    }

    private static void pump(InputStream input, OutputStream output) {
        try {
            input.transferTo(output)
        }
        catch (IOException e) {
            // ignore - connection closed
        }
    }
}
