package io.seqera.controller

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.exchange.SubmitContainerTokenRequest
import io.seqera.exchange.SubmitContainerTokenResponse
import io.seqera.service.ContainerRequestData
import io.seqera.service.ContainerTokenService
import io.seqera.service.UserService
import jakarta.inject.Inject
/**
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
        final data = new ContainerRequestData(userId: user.id, workspaceId: req.towerWorkspaceId, containerImage: req.containerImage)
        final token = tokenService.getToken(data)
        HttpResponse.ok(new SubmitContainerTokenResponse(containerToken: token))
    }
}
