package io.seqera.wave.controller

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.builder.ContainerBuildService
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

    @Inject
    ContainerBuildService buildService

    @PostConstruct
    private void init() {
        log.info "Wave server url: $serverUrl; allowAnonymous: $allowAnonymous"
    }

    @Post
    HttpResponse<SubmitContainerTokenResponse> getToken(SubmitContainerTokenRequest req) {
        final Long userId = req.towerAccessToken
                ? userService.getUserByAccessToken(req.towerAccessToken).id
                : 0
        if( !userId && !allowAnonymous )
            throw new BadRequestException("Missing access token")

        final data = makeRequestData(req, userId)
        final token = tokenService.getToken(data)
        final target = targetImage(token, data.containerImage)
        final resp = new SubmitContainerTokenResponse(containerToken: token, targetImage: target)
        HttpResponse.ok(resp)
    }

    ContainerRequestData makeRequestData(SubmitContainerTokenRequest req, Long userId) {
        if( !req.containerImage && !req.containerFile )
            throw new BadRequestException("Missing container image")

        String targetImage
        String targetContent
        String condaContent
        if( req.containerFile ) {
            targetContent = new String(req.containerFile.decodeBase64())
            condaContent = req.condaFile ? new String(req.condaFile.decodeBase64()) : null
            targetImage = buildService.buildImage(targetContent, condaContent)
        }
        else {
            targetImage = req.containerImage
            targetContent = null
            condaContent = null
        }

        final data = new ContainerRequestData(
                userId,
                req.towerWorkspaceId,
                targetImage,
                targetContent,
                req.containerConfig,
                condaContent )

        return data
    }

    protected String targetImage(String token, String image) {
        final coords = ContainerCoordinates.parse(image)
        return "${new URL(serverUrl).getAuthority()}/wt/$token/${coords.image}:${coords.reference}"
    }
}
