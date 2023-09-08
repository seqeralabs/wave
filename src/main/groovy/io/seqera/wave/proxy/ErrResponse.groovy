/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.proxy

import javax.net.ssl.SSLSession
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ErrResponse<T> implements HttpResponse<T> {

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

    @Override
    int statusCode() {
        return statusCode
    }

    @Override
    HttpRequest request() {
        return request
    }

    @Override
    Optional<HttpResponse<T>> previousResponse() {
        return null
    }

    @Override
    HttpHeaders headers() {
        return headers
    }

    @Override
    T body() {
        return body
    }

    @Override
    Optional<SSLSession> sslSession() {
        return null
    }

    @Override
    URI uri() {
        return uri
    }

    @Override
    HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1
    }

    static ErrResponse<String> forString(String msg, HttpRequest request) {
        final head = HttpHeaders.of('Content-Type': ['text/plain'], {true})
        new ErrResponse<String>(statusCode: 400, body: msg, request: request, uri: request.uri(), headers: head)
    }

    static ErrResponse<InputStream> forStream(String msg, HttpRequest request) {
        final head = HttpHeaders.of('Content-Type': ['text/plain'], {true})
        new ErrResponse<InputStream>(statusCode: 400, body: new ByteArrayInputStream(msg.bytes), request: request, uri: request.uri(), headers: head)
    }

    static ErrResponse<byte[]> forByteArray(String msg, HttpRequest request) {
        final head = HttpHeaders.of('Content-Type': ['text/plain'], {true})
        new ErrResponse<byte[]>(statusCode: 400, body: msg.bytes, request: request, uri: request.uri(), headers: head)
    }
}
