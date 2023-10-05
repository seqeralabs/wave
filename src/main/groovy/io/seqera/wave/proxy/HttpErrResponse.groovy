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
package io.seqera.wave.proxy

import groovy.transform.CompileStatic
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpVersion
import io.micronaut.http.MutableHttpHeaders

/**
 *
 * @author Munish Chouhan <munish.chouhan@gmail.com>
 */
@CompileStatic
class HttpErrResponse<T> implements HttpResponse<T> {

    private int statusCode

    private T body

    /**
     *  the HttpRequest corresponding to this response.
     */
    private HttpRequest request

    /**
     *  the URI that the response was received from. This may be different from the request URI if redirection occurred.
     */
    private URI uri

    private HttpHeaders headers

    private MutableConvertibleValues<Object> attributes
    @Override
    HttpStatus getStatus() {
        return HttpStatus.valueOf(statusCode)
    }

    @Override
    HttpHeaders getHeaders() {
        return headers
    }

    @Override
    MutableConvertibleValues<Object> getAttributes() {
        return null
    }

    @Override
    Optional getBody() {
        return Optional.of(body)
    }

    URI uri() {
        return uri
    }

    static HttpVersion version() {
        return HttpVersion.HTTP_1_1
    }

    static HttpErrResponse<String> forString(String msg, HttpRequest request) {
        new HttpErrResponse<String>(statusCode: 400, body: msg, request: request, uri: request.uri, headers: request.headers)
    }

    static HttpErrResponse<InputStream> forStream(String msg, HttpRequest request) {
        new HttpErrResponse<InputStream>(statusCode: 400, body: new ByteArrayInputStream(msg.bytes), request: request, uri: request.uri, headers: request.headers)
    }

    static HttpErrResponse<byte[]> forByteArray(String msg, HttpRequest request) {
        new HttpErrResponse<byte[]>(statusCode: 400, body: msg.bytes, request: request, uri: request.uri, headers: request.headers)
    }
}
