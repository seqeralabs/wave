package io.seqera.wave.controller

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.wave.exchange.SubmitContainerTokenRequest
import io.seqera.wave.exchange.SubmitContainerTokenResponse
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.ContainerTokenService
import io.seqera.wave.service.UserService
import jakarta.inject.Inject
/**
 * Implement a controller to receive container token requests
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Controller("/container-token")
class ContainerTokenController {

    @Inject ContainerTokenService tokenService
    @Inject UserService userService

    @Post
    HttpResponse<SubmitContainerTokenResponse> getToken(SubmitContainerTokenRequest req) {
        final user = userService.getUserByAccessToken(req.towerAccessToken)
        if( !user )
            HttpResponse.badRequest()
        final data = new ContainerRequestData(user.id, req.towerWorkspaceId, req.containerImage)
        final token = tokenService.getToken(data)
        HttpResponse.ok(new SubmitContainerTokenResponse(containerToken: token))
    }
}
