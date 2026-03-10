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

package io.seqera.wave

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.security.authentication.AuthorizationException
import io.seqera.wave.exception.BuildTimeoutException
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.exception.ForbiddenException
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.exception.RegistryForwardException
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.exception.WaveException
import io.seqera.random.LongRndKey
import io.seqera.wave.util.RegHelper
import jakarta.inject.Singleton
/**
 * Common error handling logic
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ErrorHandler {

    static interface Mapper<T> {
        T apply(String message, String errorCode)
    }

    @Value('${wave.debug:false}')
    private Boolean debug

    <T> HttpResponse<T> handle(HttpRequest request, Throwable t, Mapper<T> responseFactory) {
        final knownException = t instanceof WaveException || t instanceof HttpStatusException
        def msg = t.message
        if( knownException && msg ) {
            // the the error cause
            if( t.cause ) msg += " - Cause: ${t.cause.message ?: t.cause}".toString()
            // render the message for logging
            String render = msg
            if( request )
                render += toString(request)
            if( !debug ) {
                log.warn(render)
            }
            else {
                log.warn(render, t)
            }
        }
        else {
            final errId = LongRndKey.rndHex()
            final errorIdSuffix = " - Error ID: ${errId}".toString()
            // Log the original message for debugging
            String logMsg = msg ?: t.cause?.message ?: "Unknown error"
            String render = logMsg
            if( request )
                render += toString(request)
            render += errorIdSuffix
            log.error(render, t)

            // In debug mode, show the original error for troubleshooting
            // In production, strip stack traces and internal class names from the client response
            if( debug )
                msg = logMsg + errorIdSuffix
            else
                msg = sanitizeErrorMessage(logMsg) + errorIdSuffix
        }

        if( t instanceof HttpStatusException ) {
            final body = (t.body.isPresent() ? t.body.get() : t.message) as T
            return HttpResponse
                    .status(t.status)
                    .body(body)
        }

        if( t instanceof RegistryForwardException ) {
            // report this error as it has been returned by the target registry
            return (HttpResponse<T>) HttpResponse
                    .status(HttpStatus.valueOf(t.statusCode))
                    .body(t.response)
                    .headers(t.headers as Map<CharSequence,CharSequence>)
        }

        if( t instanceof DockerRegistryException ) {
            final resp = responseFactory.apply(msg, t.error)
            return HttpResponseFactory.INSTANCE.status(t.statusCode).body(resp)
        }

        if( t instanceof UnauthorizedException )
            return HttpResponse.unauthorized()

        if( t instanceof ForbiddenException )
            return HttpResponseFactory.INSTANCE.status(HttpStatus.FORBIDDEN)

        if( t instanceof NotFoundException ) {
            final resp = responseFactory.apply(msg, 'NOT_FOUND')
            return HttpResponse.notFound(resp)
        }

        if( t instanceof SlowDownException ) {
            final resp = responseFactory.apply(msg, 'TOOMANYREQUESTS')
            return HttpResponseFactory.INSTANCE.status(HttpStatus.TOO_MANY_REQUESTS).body(resp)
        }

        if( t instanceof BuildTimeoutException ) {
            final resp = responseFactory.apply(msg, 'TIMEOUT')
            return HttpResponseFactory.INSTANCE.status(HttpStatus.REQUEST_TIMEOUT).body(resp)
        }

        if( t instanceof HttpResponseException ) {
            final resp = responseFactory.apply(t.message, t.statusCode().name())
            return HttpResponseFactory.INSTANCE.status(t.statusCode()).body(resp)
        }

        if( t instanceof WaveException ) {
            final resp = responseFactory.apply(msg, 'BAD_REQUEST')
            return HttpResponse.badRequest(resp)
        }

        if( t instanceof AuthorizationException ) {
            return HttpResponse.unauthorized()
        }

        final resp = responseFactory.apply(msg, 'SERVER_ERROR')
        return HttpResponse.serverError(resp)

    }

    /**
     * Strip stack traces and internal class names from error messages
     * while keeping the useful error description intact
     */
    static String sanitizeErrorMessage(String message) {
        if( !message )
            return "Unexpected error"
        // take only the first line to remove stack traces
        def result = message.split('\n')[0].trim()
        // strip "Failed to convert argument ... due to:" prefix
        final dueToIdx = result.indexOf('due to:')
        if( dueToIdx >= 0 )
            result = result.substring(dueToIdx + 7).trim()
        // strip fully qualified class names (e.g. io.seqera.wave.api.PackagesSpec$Type)
        result = result.replaceAll(/`?[a-z]+(\.[a-z]+)+\.[A-Z][\w$]*`?/, '').trim()
        // strip Jackson source references (e.g. "at [Source: ...]")
        result = result.replaceAll(/\s*at \[Source:.*/, '').trim()
        // strip reference chains (e.g. "(through reference chain: ...)")
        result = result.replaceAll(/\(through reference chain:.*?\)/, '').trim()
        // strip quoted user input values to prevent XSS reflection (e.g. from String "user-input": ...)
        result = result.replaceAll(/from String ".*?"(:[^"']*)?\s*/, '').trim()
        // collapse multiple spaces
        result = result.replaceAll(/\s{2,}/, ' ').trim()
        return result ?: "Unexpected error"
    }

    static String toString(HttpRequest request) {
        "\n- Request: [${request.methodName}] ${request.uri}\n- Headers:${RegHelper.dumpHeaders(request)}"
    }
}
