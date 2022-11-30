package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.util.HttpHostResolver
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
class RegisterController {

    @Inject
    private SecurityService securityService

    @Inject
    private HttpHostResolver hostResolver

    @Post
    HttpResponse<RegisterInstanceResponse> register(RegisterInstanceRequest req, HttpRequest httpRequest) {
        final hostName = hostResolver.resolve(httpRequest)
        final key = securityService.getPublicKey(req.service, req.instanceId, hostName)
        final result = new RegisterInstanceResponse(publicKey: key)
        return HttpResponse.ok(result)
    }
}
