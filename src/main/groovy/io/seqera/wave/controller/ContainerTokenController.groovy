package io.seqera.wave.controller

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.auth.DockerAuthService
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.UserService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.token.ContainerTokenService
import io.seqera.wave.tower.User
import io.seqera.wave.util.DataTimeUtils
import jakarta.inject.Inject
/**
 * Implement a controller to receive container token requests
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/container-token")
class ContainerTokenController {

    @Inject HttpClientAddressResolver addressResolver
    @Inject ContainerTokenService tokenService
    @Inject UserService userService

    @Inject
    @Value('${wave.allowAnonymous}')
    Boolean allowAnonymous

    @Inject
    @Value('${wave.server.url}')
    String serverUrl

    /**
     * The registry repository where the build image will be stored
     */
    @Value('${wave.build.repo}')
    String defaultBuildRepo

    @Value('${wave.build.cache}')
    String defaultCacheRepo

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    String workspace

    @Inject
    ContainerBuildService buildService

    @Inject
    DockerAuthService dockerAuthService

    @Inject
    RegistryProxyService registryProxyService

    @PostConstruct
    private void init() {
        log.info "Wave server url: $serverUrl; allowAnonymous: $allowAnonymous"
    }

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

    protected HttpResponse<SubmitContainerTokenResponse> makeResponse(HttpRequest httpRequest, SubmitContainerTokenRequest req, User user) {
        if( !user && !allowAnonymous )
            throw new BadRequestException("Missing access token")
        final ip = addressResolver.resolve(httpRequest)
        final data = makeRequestData(req, user, ip)
        final token = tokenService.computeToken(data)
        final target = targetImage(token, data.containerImage)
        final resp = new SubmitContainerTokenResponse(containerToken: token, targetImage: target)
        return HttpResponse.ok(resp)
    }

    BuildRequest makeBuildRequest(SubmitContainerTokenRequest req, User user, String ip) {
        if( !req.containerFile )
            throw new BadRequestException("Missing dockerfile content")
        if( !defaultBuildRepo )
            throw new BadRequestException("Missing build repository attribute")
        if( !defaultCacheRepo )
            throw new BadRequestException("Missing build cache repository attribute")
        final dockerContent = new String(req.containerFile.decodeBase64())
        final condaContent = req.condaFile ? new String(req.condaFile.decodeBase64()) : null as String
        final platform = ContainerPlatform.of(req.containerPlatform)
        final build = req.buildRepository ?: defaultBuildRepo
        final cache = req.cacheRepository ?: defaultCacheRepo
        final configJson = dockerAuthService.credentialsConfigJson(dockerContent, build, cache, user?.id, req.towerWorkspaceId)
        final offset = DataTimeUtils.offsetId(req.timestamp)
        // create a unique digest to identify the request
        return new BuildRequest(
                dockerContent,
                Path.of(workspace),
                build,
                condaContent,
                user,
                platform,
                configJson,
                cache,
                ip,
                offset )
    }

    protected BuildRequest buildRequest(SubmitContainerTokenRequest req, User user, String ip) {
        final build = makeBuildRequest(req, user, ip)
        if( req.forceBuild )  {
            log.debug "Build forced for container image '$build.targetImage'"
            buildService.buildImage(build)
        }
        else if( !registryProxyService.isManifestPresent(build.targetImage) ) {
            buildService.buildImage(build)
        }
        else {
            log.debug "== Found cached build for request: $build"
        }
        return build
    }

    ContainerRequestData makeRequestData(SubmitContainerTokenRequest req, User user, String ip) {
        if( req.containerImage && req.containerFile )
            throw new BadRequestException("Attributes 'containerImage' and 'containerFile' cannot be used in the same request")

        String targetImage
        String targetContent
        String condaContent
        if( req.containerFile ) {
            final build = buildRequest(req, user, ip)
            targetImage = build.targetImage
            targetContent = build.dockerFile
            condaContent = build.condaFile
        }
        else if( req.containerImage ) {
            targetImage = req.containerImage
            targetContent = null
            condaContent = null
        }
        else
            throw new BadRequestException("Specify either 'containerImage' or 'containerFile' attribute")

        final data = new ContainerRequestData(
                user?.id,
                req.towerWorkspaceId,
                targetImage,
                targetContent,
                req.containerConfig,
                condaContent,
                ContainerPlatform.of(req.containerPlatform) )

        return data
    }

    protected String targetImage(String token, String image) {
        final coords = ContainerCoordinates.parse(image)
        return "${new URL(serverUrl).getAuthority()}/wt/$token/${coords.image}:${coords.reference}"
    }
}
