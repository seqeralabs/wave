package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.wave.exchange.SubmitContainerTokenRequest
import io.seqera.wave.exchange.SubmitContainerTokenResponse
import io.seqera.wave.model.ContainerCoordinates
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
@CompileStatic
@Controller("/container-token")
class ContainerTokenController {

    @Inject ContainerTokenService tokenService
    @Inject UserService userService

    @Inject
    @Value('${wave.allowAnonymous}')
    Boolean allowAnonymous

    @Inject
    @Value('${wave.server.url}')
    String serverUrl

    @Post
    HttpResponse<SubmitContainerTokenResponse> getToken(SubmitContainerTokenRequest req) {
        final Long userId = req.towerAccessToken
                ? userService.getUserByAccessToken(req.towerAccessToken).id
                : 0
        if( !userId && !allowAnonymous )
            HttpResponse.badRequest()

        final data = new ContainerRequestData(
                userId,
                req.towerWorkspaceId,
                req.containerImage,
                req.containerFile,
                req.containerConfig )
        final token = tokenService.getToken(data)
        final target = targetImage(token, req.containerImage)
        final resp = new SubmitContainerTokenResponse(containerToken: token, targetImage: target)
        HttpResponse.ok(resp)
    }

    protected String targetImage(String token, String image) {
        final coords = ContainerCoordinates.parse(image)
        return "${new URL(serverUrl).getAuthority()}/wt/$token/${coords.image}:${coords.reference}"
    }
}
