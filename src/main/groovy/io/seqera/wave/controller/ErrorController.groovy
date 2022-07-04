package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.seqera.wave.exception.ForbiddenException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.exception.WaveException
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.exchange.ErrorResponse
import io.seqera.wave.util.LongRndKey

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller('/error')
class ErrorController {

    @Value('${wave.debug}')
    Boolean debug

    @Error(global = true)
    HttpResponse handleException(HttpRequest request, Throwable exception) {
        handle0(exception, debug)
    }

    static protected HttpResponse<ErrorResponse> handle0(Throwable t, boolean debug) {
        def msg = t.message
        if( t instanceof WaveException && msg ) {
            log.warn (t.cause ? "$msg -- Cause: ${t.cause.message ?: t.cause}".toString() : msg )
        }
        else {
            if( debug && !msg )
                msg = t.cause?.message
            if ( !debug || !msg )
                msg = "Oops... Unable to process request"
            msg += " - Error ID: ${LongRndKey.rndHex()}"
            log.error(msg, t)
        }
        final resp = new ErrorResponse(msg)

        if( t instanceof UnauthorizedException )
            return HttpResponse.unauthorized()
        if( t instanceof ForbiddenException )
            return HttpResponseFactory.INSTANCE.status(HttpStatus.FORBIDDEN)
        else if( t instanceof NotFoundException )
            return HttpResponse.notFound(resp)
        else
            return HttpResponse.badRequest(resp)
    }
}
