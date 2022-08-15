package io.seqera.wave

import java.util.function.BiFunction

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.seqera.wave.exception.ForbiddenException
import io.seqera.wave.exception.GenericException
import io.seqera.wave.exception.NotFoundException
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

    @Value('${wave.debug}')
    private Boolean debug

    def <T> HttpResponse<T> handle(HttpRequest request, Throwable t, BiFunction<String,String,T> responseFactory) {
        final errId = LongRndKey.rndHex()
        def msg = t.message
        if( t instanceof WaveException && msg ) {
            log.warn (t.cause ? "$msg -- Cause: ${t.cause.message ?: t.cause}".toString() : msg )
        }
        else {
            if( debug && !msg )
                msg = t.cause?.message
            if ( !debug || !msg )
                msg = "Oops... Unable to process request"
            msg += " - Error ID: ${errId}"
            log.error(msg, t)
        }

        final resp = responseFactory.apply(msg, errId)
        if( t instanceof GenericException )
            return HttpResponseFactory.INSTANCE.status(t.statusCode).body(resp)

        if( t instanceof UnauthorizedException )
            return HttpResponse.unauthorized()
        if( t instanceof ForbiddenException )
            return HttpResponseFactory.INSTANCE.status(HttpStatus.FORBIDDEN)

        if( t instanceof NotFoundException )
            return HttpResponse.notFound(resp)
        else
            return HttpResponse.badRequest(resp)
    }

}
