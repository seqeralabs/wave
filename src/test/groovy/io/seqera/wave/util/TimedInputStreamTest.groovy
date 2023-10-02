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

package io.seqera.wave.util

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TimedInputStreamTest extends Specification {

    public static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(characters.length());
            char randomChar = characters.charAt(randomIndex);
            stringBuilder.append(randomChar);
        }

        return stringBuilder.toString();
    }

    def 'should read small string' () {
        when:
        def buffer = new ByteArrayInputStream("Hello world".bytes)
        def stream = new TimedInputStream(buffer, Duration.ofMinutes(1))
        then:
        stream.text == "Hello world"
    }


    def 'should read long string'() {
        given:
        def data = generateRandomString(1024 * 1024)
        when:
        def buffer = new ByteArrayInputStream(data.bytes)
        def stream = new TimedInputStream(buffer, Duration.ofMinutes(1))
        then:
        stream.text == data
    }

    def 'should read byte bytes' () {
        given:
        def data = generateRandomString(1024 * 1024)
        when:
        def buffer = new ByteArrayInputStream(data.bytes)
        and:
        def stream = new TimedInputStream(buffer, Duration.ofMinutes(1))
        def result = new ByteArrayOutputStream()
        int ch
        while( (ch=stream.read())!=-1 ) {
            result.write(ch)            
        }
        and:
        stream.close()
        then:
        new String(result.toByteArray()) == data

    }

    def 'should read data from socket' () {
        given:
        def port = 9911
        ServerSocket serverSocket = new ServerSocket(port);
        def data = "Hello world"
        and:
        Thread.start {
            Socket clientSocket = serverSocket.accept();
            def writer = new PrintWriter(clientSocket.getOutputStream())
            writer.print(data)
            writer.close()
        }
        and:
        Socket socket = new Socket("localhost", port);

        when:
        def stream = new TimedInputStream(socket.getInputStream(), Duration.ofSeconds(5))
        then:
        stream.text == data

        cleanup:
        serverSocket.close()
    }

    def 'should timeout because socket is not closed' () {
        given:
        def port = 9911
        ServerSocket serverSocket = new ServerSocket(port);
        def data = "Hello world"
        and:
        Thread.start {
            Socket clientSocket = serverSocket.accept();
            def writer = new PrintWriter(clientSocket.getOutputStream())
            writer.print(data)
        }
        and:
        Socket socket = new Socket("localhost", port);

        when:
        def stream = new TimedInputStream(socket.getInputStream(), Duration.ofSeconds(5))
        and:
        stream.getText()
        then:
        thrown(TimeoutException)

        cleanup:
        serverSocket.close()
    }
}
