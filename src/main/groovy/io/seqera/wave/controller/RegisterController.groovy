package io.seqera.wave.controller

import javax.validation.ConstraintViolationException
import javax.validation.Valid

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.util.HttpHostResolver
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
@Controller("/register")
@Validated
class RegisterController {

    @Inject
    private SecurityService securityService

    @Inject
    private HttpHostResolver hostResolver

    @Post
    @Consumes()
    HttpResponse<RegisterInstanceResponse> register(@Body @Valid RegisterInstanceRequest req, HttpRequest httpRequest) {
        final hostName = hostResolver.resolve(httpRequest)
        final key = securityService.getPublicKey(req.service, req.instanceId, hostName)
        return HttpResponse.ok(key)
    }

    @Error(exception = ConstraintViolationException)
    HttpResponse onValidationFailure(HttpRequest _req, ConstraintViolationException e) {
        return HttpResponse.badRequest(e.message)
    }

    // Used if the body is invalid json
    @Error(exception = ConversionErrorException)
    HttpResponse onConversionFailure(HttpRequest _req,ConversionErrorException e) {
        return HttpResponse.badRequest(e.message)
    }

}
