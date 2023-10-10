/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.controller

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.exchange.DescribeWaveContainerResponse
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.UserService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.builder.FreezeService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.token.ContainerTokenService
import io.seqera.wave.service.token.TokenData
import io.seqera.wave.service.validation.ValidationService
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.util.DataTimeUtils
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Inject
import static io.seqera.wave.WaveDefault.TOWER
import static io.seqera.wave.service.builder.BuildFormat.DOCKER
import static io.seqera.wave.service.builder.BuildFormat.SINGULARITY
import static io.seqera.wave.util.SpackHelper.prependBuilderTemplate

/**
 * Implement a controller to receive container token requests
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.IO)
class ContainerTokenController {

    @Inject HttpClientAddressResolver addressResolver
    @Inject ContainerTokenService tokenService
    @Inject UserService userService
    @Inject PairingService securityService
    @Inject JwtAuthStore jwtAuthStore

    @Inject
    @Value('${wave.allowAnonymous}')
    Boolean allowAnonymous

    @Inject
    @Value('${wave.server.url}')
    String serverUrl

    @Inject
    @Value('${tower.endpoint.url:`https://api.tower.nf`}')
    String towerEndpointUrl

    /**
     * The registry repository where the build image will be stored
     */
    @Value('${wave.build.repo}')
    String defaultBuildRepo

    @Value('${wave.build.cache}')
    String defaultCacheRepo

    @Nullable
    @Value('${wave.build.public}')
    String defaultPublicRepo

    @Value('${wave.scan.enabled:false}')
    boolean scanEnabled

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    String workspace

    @Inject
    ContainerBuildService buildService

    @Inject
    ContainerInspectService dockerAuthService

    @Inject
    RegistryProxyService registryProxyService

    @Inject
    PersistenceService persistenceService

    @Inject
    ValidationService validationService

    @Inject
    PairingService pairingService

    @Inject
    PairingChannel pairingChannel

    @Inject FreezeService freezeService

    @PostConstruct
    private void init() {
        log.info "Wave server url: $serverUrl; allowAnonymous: $allowAnonymous; tower-endpoint-url: $towerEndpointUrl; default-builld-repo: $defaultBuildRepo; default-cache-repo: $defaultCacheRepo; default-public-repo: $defaultPublicRepo"
    }

    @Post('/container-token')
    CompletableFuture<HttpResponse<SubmitContainerTokenResponse>> getToken(HttpRequest httpRequest, SubmitContainerTokenRequest req) {
        validateContainerRequest(req)

        // this is needed for backward compatibility with old clients
        if( !req.towerEndpoint ) {
            req.towerEndpoint = towerEndpointUrl
        }

        // anonymous access
        if( !req.towerAccessToken ) {
            return CompletableFuture.completedFuture(makeResponse(httpRequest, req, null))
        }

        // We first check if the service is registered
        final registration = securityService.getPairingRecord(PairingService.TOWER_SERVICE, req.towerEndpoint)
        if( !registration )
            throw new BadRequestException("Tower instance '${req.towerEndpoint}' has not enabled to connect Wave service '$serverUrl'")

        // store the tower JWT tokens
        jwtAuthStore.putJwtAuth(req.towerEndpoint, req.towerRefreshToken, req.towerAccessToken)
        // find out the user associated with the specified tower access token
        return userService
                .getUserByAccessTokenAsync(registration.endpoint, req.towerAccessToken)
                .thenApply { User user -> makeResponse(httpRequest, req, user) }
    }

    protected HttpResponse<SubmitContainerTokenResponse> makeResponse(HttpRequest httpRequest, SubmitContainerTokenRequest req, User user) {
        if( !user && !allowAnonymous )
            throw new BadRequestException("Missing user access token")
        final ip = addressResolver.resolve(httpRequest)
        final data = makeRequestData(req, user, ip)
        final token = tokenService.computeToken(data)
        final target = targetImage(token.value, data.coordinates())
        final build = data.buildNew ? data.buildId : null
        final resp = new SubmitContainerTokenResponse(token.value, target, token.expiration, data.containerImage, build)
        // persist request
        storeContainerRequest0(req, data, user, token, target, ip)
        // return response
        return HttpResponse.ok(resp)
    }

    protected void storeContainerRequest0(SubmitContainerTokenRequest req, ContainerRequestData data, User user, TokenData token, String target, String ip) {
        try {
            final recrd = new WaveContainerRecord(req, data, target, user, ip, token.expiration)
            persistenceService.saveContainerRequest(token.value, recrd)
        }
        catch (Throwable e) {
            log.error("Unable to store container request with token: ${token}", e)
        }
    }

    BuildRequest makeBuildRequest(SubmitContainerTokenRequest req, User user, String ip) {
        if( !req.containerFile )
            throw new BadRequestException("Missing dockerfile content")
        if( !defaultBuildRepo )
            throw new BadRequestException("Missing build repository attribute")
        if( !defaultCacheRepo )
            throw new BadRequestException("Missing build cache repository attribute")

        final containerSpec = new String(req.containerFile.decodeBase64())
        final condaContent = req.condaFile ? new String(req.condaFile.decodeBase64()) : null as String
        final spackContent = req.spackFile ? new String(req.spackFile.decodeBase64()) : null as String
        final format = req.formatSingularity() ? SINGULARITY : DOCKER
        final platform = ContainerPlatform.of(req.containerPlatform)
        final build = req.buildRepository ?: (req.freeze && defaultPublicRepo ? defaultPublicRepo : defaultBuildRepo)
        final cache = req.cacheRepository ?: defaultCacheRepo
        final configJson = dockerAuthService.credentialsConfigJson(containerSpec, build, cache, user?.id, req.towerWorkspaceId, req.towerAccessToken, req.towerEndpoint, req.towerWorkflowId)
        final containerConfig = req.freeze ? req.containerConfig : null
        final offset = DataTimeUtils.offsetId(req.timestamp)
        final scanId = scanEnabled && format==DOCKER ? LongRndKey.rndHex() : null
        // create a unique digest to identify the request
        return new BuildRequest(
                (spackContent ? prependBuilderTemplate(containerSpec,format) : containerSpec),
                Path.of(workspace),
                build,
                condaContent,
                spackContent,
                format,
                user,
                containerConfig,
                req.buildContext,
                platform,
                configJson,
                cache,
                scanId,
                ip,
                offset)
    }

    protected BuildRequest buildRequest(SubmitContainerTokenRequest req, User user, String ip) {
        final build = makeBuildRequest(req, user, ip)
        if( req.dryRun ) {
            log.debug "== Dry-run build request: $build"
            return build
        }
        if( !registryProxyService.isManifestPresent(build.targetImage) ) {
            buildService.buildImage(build)
        }
        else {
            log.debug "== Found cached build for request: $build"
        }
        return build
    }

    ContainerRequestData makeRequestData(SubmitContainerTokenRequest req, User user, String ip) {
        if( !req.containerImage && !req.containerFile )
            throw new BadRequestException("Specify either 'containerImage' or 'containerFile' attribute")
        if( req.containerImage && req.containerFile )
            throw new BadRequestException("Attributes 'containerImage' and 'containerFile' cannot be used in the same request")
        if( req.containerImage?.contains('@sha256:') && req.containerConfig && !req.freeze )
            throw new BadRequestException("Container requests made using a SHA256 as tag does not support the 'containerConfig' attribute")
        if( req.freeze && !req.buildRepository && !defaultPublicRepo )
            throw new BadRequestException("When freeze mode is enabled the target build repository must be specified - see 'wave.build.repository' setting")
        if( req.formatSingularity() && !req.freeze )
            throw new BadRequestException("Singularity build is only allowed enabling freeze mode - see 'wave.freeze' setting")

        // when 'freeze' is enabled, rewrite the request so that the container configuration specified
        // in the request is included in the build container file instead of being processed via the augmentation process
        if( req.freeze ) {
            req = freezeService.freezeBuildRequest(req, user)
        }

        String targetImage
        String targetContent
        String condaContent
        String buildId
        boolean buildNew
        if( req.containerFile ) {
            final build = buildRequest(req, user, ip)
            targetImage = build.targetImage
            targetContent = build.containerFile
            condaContent = build.condaFile
            buildId = build.id
            buildNew = build.uncached
        }
        else if( req.containerImage ) {
            // normalize container image
            final coords = ContainerCoordinates.parse(req.containerImage)
            targetImage = coords.getTargetContainer()
            targetContent = null
            condaContent = null
            buildId = null
            buildNew = null
        }
        else
            throw new IllegalStateException("Specify either 'containerImage' or 'containerFile' attribute")

        new ContainerRequestData(
                user?.id,
                req.towerWorkspaceId,
                targetImage,
                targetContent,
                req.containerConfig,
                condaContent,
                ContainerPlatform.of(req.containerPlatform),
                // this token is the original one used in the submit container request
                // in the presence of a refresh token this is used just as a key to retrieve
                // the actual authorization tokens from the store
                req.towerAccessToken,
                req.towerEndpoint,
                buildId,
                buildNew,
                req.freeze,
                req.towerWorkflowId)
    }

    protected String targetImage(String token, ContainerCoordinates container) {
        return "${new URL(serverUrl).getAuthority()}/wt/$token/${container.getImageAndTag()}"
    }

    @Get('/container-token/{token}')
    HttpResponse<DescribeWaveContainerResponse> describeContainerRequest(String token) {
        final data = persistenceService.loadContainerRequest(token)
        if( !data )
            throw new NotFoundException("Missing container record for token: $token")
        // return the response 
        return HttpResponse.ok( DescribeWaveContainerResponse.create(token, data) )
    }

    void validateContainerRequest(SubmitContainerTokenRequest req) throws BadRequestException{
        if( req.towerEndpoint && req.towerAccessToken ) {

            // check the endpoint is valid public URL if not registered
            if( !pairingChannel.canHandle(PairingService.TOWER_SERVICE, req.towerEndpoint) ) {
                final msg = validationService.checkEndpoint(req.towerEndpoint)
                if (msg)
                    throw new BadRequestException(msg.replaceAll(/(?i)endpoint/, 'Tower endpoint'))
            }

            // check the endpoint has been registered via the pairing process
            if( !pairingService.getPairingRecord(TOWER, req.towerEndpoint) )
                throw new BadRequestException("Missing pairing record for Tower endpoint '$req.towerEndpoint'")
        }

        String msg
        // check valid image name
        msg = validationService.checkContainerName(req.containerImage)
        if( msg ) throw new BadRequestException(msg)
        // check build repo
        msg = validationService.checkBuildRepository(req.buildRepository, false)
        if( msg ) throw new BadRequestException(msg)
        // check cache repository
        msg = validationService.checkBuildRepository(req.cacheRepository, true)
        if( msg ) throw new BadRequestException(msg)
    }
}
