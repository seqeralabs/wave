package io.seqera.wave

import java.util.function.BiFunction

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.seqera.wave.exception.BuildTimeoutException
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.exception.ForbiddenException
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.exception.WaveException
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Singleton
/**
 * Common error handling logic
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
class ErrorHandler {

    @Value('${wave.debug:false}')
    private Boolean debug

    def <T> HttpResponse<T> handle(HttpRequest httpRequest, Throwable t, BiFunction<String,String,T> responseFactory) {
        final errId = LongRndKey.rndHex()
        final request = httpRequest?.toString()
        def msg = t.message
        if( t instanceof WaveException && msg ) {
            // the the error cause
            if( t.cause ) msg += " - Cause: ${t.cause.message ?: t.cause}".toString()
            // render the message for logging
            def render = msg
            if( request ) render += " - Request: ${request}"
            if( !debug ) {
                log.warn(render)
            }
            else {
                log.warn(render, t)
            }
        }
        else {
            if( debug && !msg )
                msg = t.cause?.message
            if ( !debug && !msg )
                msg = "Oops... Unable to process request"
            msg += " - Error ID: ${errId}"
            // render the message for logging
            def render = msg
            if( request ) render += " - Request: ${request}"
            log.error(render, t)
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
            final resp = responseFactory.apply(msg, 'DENIED')
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

        final resp = responseFactory.apply(msg, 'SERVER_ERROR')
        return HttpResponse.serverError(resp)

    }

}
