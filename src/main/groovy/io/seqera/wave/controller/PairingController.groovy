package io.seqera.wave.controller

import javax.validation.ConstraintViolationException
import javax.validation.Valid

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.seqera.wave.exchange.PairServiceRequest
import io.seqera.wave.exchange.PairServiceResponse
import io.seqera.wave.exchange.PairingRequest
import io.seqera.wave.exchange.PairingResponse
import io.seqera.wave.service.security.PairingService
import jakarta.inject.Inject
/**
 * Allow a remote Tower instance to register itself
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@Validated
class PairingController {

    @Inject
    private PairingService securityService

    @Post('/pairing')
    HttpResponse<PairingResponse> pairService(@Valid @Body PairingRequest req) {
        final key = securityService.getPairingKey(req.service, req.endpoint)
        return HttpResponse.ok(key)
    }

    @Deprecated
    @Post('/pair-service')
    HttpResponse<PairServiceResponse> pairService(@Valid @Body PairServiceRequest req) {
        final key = securityService.getPublicKey(req.service, req.endpoint)
        return HttpResponse.ok(key)
    }

    @Error(exception = ConstraintViolationException)
    HttpResponse onValidationFailure(HttpRequest _req, ConstraintViolationException e) {
        return HttpResponse.badRequest(e.message)
    }

    // Used if the body provides an invalid JSON
    @Error(exception = ConversionErrorException)
    HttpResponse onConversionFailure(HttpRequest _req,ConversionErrorException e) {
        return HttpResponse.badRequest(e.message)
    }

}
