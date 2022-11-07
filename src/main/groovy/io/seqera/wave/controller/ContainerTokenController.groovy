package io.seqera.wave.controller

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestService
import io.seqera.wave.service.UserService
import io.seqera.wave.service.token.ContainerTokenService
import io.seqera.wave.tower.User
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

    @Inject
    private HttpClientAddressResolver addressResolver

    @Inject
    private ContainerTokenService tokenService

    @Inject
    private UserService userService

    @Inject
    @Value('${wave.allowAnonymous}')
    private Boolean allowAnonymous

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Inject
    private ContainerRequestService containerRequestService

    @Post
    CompletableFuture<HttpResponse<SubmitContainerTokenResponse>> getToken(HttpRequest httpRequest, SubmitContainerTokenRequest req) {
        if( req.towerAccessToken ) {
            return userService
                    .getUserByAccessTokenAsync(req.towerAccessToken)
                    .thenApply( user-> makeResponse(httpRequest, req, user))
        }
        else{
            return CompletableFuture.completedFuture(makeResponse(httpRequest, req, null))
        }
    }

    HttpResponse<SubmitContainerTokenResponse> makeResponse(HttpRequest httpRequest, SubmitContainerTokenRequest req, User user) {
        if( !user && !allowAnonymous )
            throw new BadRequestException("Missing access token")
        final ip = addressResolver.resolve(httpRequest)
        final data = containerRequestService.makeRequestData(req, user, ip)
        final token = tokenService.computeToken(data)
        final target = targetImage(token, data.containerImage)
        final resp = new SubmitContainerTokenResponse(containerToken: token, targetImage: target)
        return HttpResponse.ok(resp)
    }

    protected String targetImage(String token, String image) {
        final coords = ContainerCoordinates.parse(image)
        return "${new URL(serverUrl).getAuthority()}/wt/$token/${coords.image}:${coords.reference}"
    }
}
