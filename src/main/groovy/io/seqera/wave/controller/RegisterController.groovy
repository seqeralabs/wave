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
import io.seqera.wave.exchange.RegisterInstanceRequest
import io.seqera.wave.exchange.RegisterInstanceResponse
import io.seqera.wave.service.security.SecurityService
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
class RegisterController {

    @Inject
    private SecurityService securityService

    @Post('/register')
    HttpResponse<RegisterInstanceResponse> register(@Valid @Body RegisterInstanceRequest req) {
        log.debug "Registering service '${req.service}' at address $req.endpoint"
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
