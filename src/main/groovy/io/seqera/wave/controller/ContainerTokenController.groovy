/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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
import java.util.concurrent.ExecutorService
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.exchange.DescribeWaveContainerResponse
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.UserService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildTrack
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.builder.FreezeService
import io.seqera.wave.service.inclusion.ContainerInclusionService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.token.ContainerTokenService
import io.seqera.wave.service.token.TokenData
import io.seqera.wave.service.validation.ValidationService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.util.DataTimeUtils
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Inject
import jakarta.inject.Named
import static io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
import static io.seqera.wave.WaveDefault.TOWER
import static io.seqera.wave.service.builder.BuildFormat.DOCKER
import static io.seqera.wave.service.builder.BuildFormat.SINGULARITY
import static ContainerHelper.condaFile0
import static ContainerHelper.decodeBase64OrFail
import static ContainerHelper.spackFile0
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
    @Inject JwtAuthStore jwtAuthStore

    @Inject
    @Value('${wave.allowAnonymous}')
    Boolean allowAnonymous

    @Inject
    @Value('${wave.server.url}')
    String serverUrl

    @Inject
    @Value('${tower.endpoint.url:`https://api.cloud.seqera.io`}')
    String towerEndpointUrl

    @Value('${wave.scan.enabled:false}')
    boolean scanEnabled

    @Inject
    BuildConfig buildConfig

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

    @Inject
    FreezeService freezeService

    @Inject
    ContainerInclusionService inclusionService

    @Inject
    @Named(TaskExecutors.IO)
    ExecutorService ioExecutor

    @PostConstruct
    private void init() {
        log.info "Wave server url: $serverUrl; allowAnonymous: $allowAnonymous; tower-endpoint-url: $towerEndpointUrl; default-build-repo: $buildConfig.defaultBuildRepository; default-cache-repo: $buildConfig.defaultCacheRepository; default-public-repo: $buildConfig.defaultPublicRepository"
    }

    @Post('/container-token')
    @ExecuteOn(TaskExecutors.IO)
    CompletableFuture<HttpResponse<SubmitContainerTokenResponse>> getToken(HttpRequest httpRequest, SubmitContainerTokenRequest req) {
        return getTokenImpl(httpRequest, req, false)
    }

    protected CompletableFuture<HttpResponse<SubmitContainerTokenResponse>> getTokenImpl(HttpRequest httpRequest, SubmitContainerTokenRequest req, boolean v2) {
        validateContainerRequest(req)

        // this is needed for backward compatibility with old clients
        if( !req.towerEndpoint ) {
            req.towerEndpoint = towerEndpointUrl
        }

        // anonymous access
        if( !req.towerAccessToken ) {
            return CompletableFuture.completedFuture(makeResponse(httpRequest, req, PlatformId.NULL, v2))
        }

        // We first check if the service is registered
        final registration = pairingService.getPairingRecord(PairingService.TOWER_SERVICE, req.towerEndpoint)
        if( !registration )
            throw new BadRequestException("Tower instance '${req.towerEndpoint}' has not enabled to connect Wave service '$serverUrl'")

        // store the tower JWT tokens
        jwtAuthStore.putJwtAuth(req.towerEndpoint, req.towerRefreshToken, req.towerAccessToken)
        // find out the user associated with the specified tower access token
        return userService
                .getUserByAccessTokenAsync(registration.endpoint, req.towerAccessToken)
                .thenApplyAsync({ User user -> makeResponse(httpRequest, req, PlatformId.of(user,req), v2) }, ioExecutor)
    }

    protected HttpResponse<SubmitContainerTokenResponse> makeResponse(HttpRequest httpRequest, SubmitContainerTokenRequest req, PlatformId identity, boolean v2) {
        if( !identity && !allowAnonymous )
            throw new BadRequestException("Missing user access token")
        if( v2 && req.containerFile && req.packages )
            throw new BadRequestException("Attribute `containerFile` and `packages` conflicts each other")
        if( v2 && req.condaFile )
            throw new BadRequestException("Attribute `condaFile` is deprecated - use `packages` instead")
        if( v2 && req.spackFile )
            throw new BadRequestException("Attribute `spackFile` is deprecated - use `packages` instead")
        if( !v2 && req.packages )
            throw new BadRequestException("Attribute `packages` is not allowed")

        if( v2 && req.packages ) {
            // generate the container file required to assemble the container
            final generated = ContainerHelper.createContainerFile(req.packages, req.formatSingularity())
            req = req.copyWith(containerFile: generated.bytes.encodeBase64().toString())
        }

        final ip = addressResolver.resolve(httpRequest)
        final data = makeRequestData(req, identity, ip)
        final token = tokenService.computeToken(data)
        final target = v2 && data.freeze
                                ? data.containerImage
                                : targetImage(token.value, data.coordinates())
        final build = v2
                                ? data.buildId
                                : (data.buildNew ? data.buildId : null)
        final Boolean cached = v2
                                ? !data.buildNew
                                : null
        final String container = !v2
                                ? data.containerImage
                                : null
        final resp = new SubmitContainerTokenResponse(token.value, target, token.expiration, container, build, cached)
        // persist request
        storeContainerRequest0(req, data, token, target, ip)
        // log the response
        log.debug "New container request fulfilled - token=$token.value; expiration=$token.expiration; container=$data.containerImage; build=$build; identity=$identity"
        // return response
        return HttpResponse.ok(resp)
    }

    protected void storeContainerRequest0(SubmitContainerTokenRequest req, ContainerRequestData data, TokenData token, String target, String ip) {
        try {
            final recrd = new WaveContainerRecord(req, data, target, ip, token.expiration)
            persistenceService.saveContainerRequest(token.value, recrd)
        }
        catch (Throwable e) {
            log.error("Unable to store container request with token: ${token}", e)
        }
    }

    BuildRequest makeBuildRequest(SubmitContainerTokenRequest req, PlatformId identity, String ip) {
        if( !req.containerFile )
            throw new BadRequestException("Missing dockerfile content")
        if( !buildConfig.defaultBuildRepository )
            throw new BadRequestException("Missing build repository attribute")
        if( !buildConfig.defaultCacheRepository )
            throw new BadRequestException("Missing build cache repository attribute")

        final containerSpec = decodeBase64OrFail(req.containerFile, 'containerFile')
        final condaContent = condaFile0(req)
        final spackContent = spackFile0(req)
        final format = req.formatSingularity() ? SINGULARITY : DOCKER
        final platform = ContainerPlatform.of(req.containerPlatform)
        final build = req.buildRepository ?: (req.freeze && buildConfig.defaultPublicRepository ? buildConfig.defaultPublicRepository : buildConfig.defaultBuildRepository)
        final cache = req.cacheRepository ?: buildConfig.defaultCacheRepository
        final configJson = dockerAuthService.credentialsConfigJson(containerSpec, build, cache, identity)
        final containerConfig = req.freeze ? req.containerConfig : null
        final offset = DataTimeUtils.offsetId(req.timestamp)
        final scanId = scanEnabled && format==DOCKER ? LongRndKey.rndHex() : null
        // create a unique digest to identify the request
        return new BuildRequest(
                (spackContent ? prependBuilderTemplate(containerSpec,format) : containerSpec),
                Path.of(buildConfig.buildWorkspace),
                build,
                condaContent,
                spackContent,
                format,
                identity,
                containerConfig,
                req.buildContext,
                platform,
                configJson,
                cache,
                scanId,
                ip,
                offset)
    }

   protected BuildTrack buildRequest(SubmitContainerTokenRequest req, PlatformId identity, String ip) {
        final build = makeBuildRequest(req, identity, ip)
        final digest = registryProxyService.getImageDigest(build.targetImage)
        // check for dry-run execution
        if( req.dryRun ) {
            log.debug "== Dry-run build request: $build"
            final dryId = build.containerId +  BuildRequest.SEP + '0'
            final cached = digest!=null
            return new BuildTrack(dryId, build.targetImage, cached)
        }
        // check for existing image
        if( digest ) {
            log.debug "== Found cached build for request: $build"
            final cache = persistenceService.loadBuild(build.targetImage, digest)
            return new BuildTrack(cache?.buildId, build.targetImage, true)
        }
        else {
            return buildService.buildImage(build)
        }
    }

    ContainerRequestData makeRequestData(SubmitContainerTokenRequest req, PlatformId identity, String ip) {
        if( !req.containerImage && !req.containerFile )
            throw new BadRequestException("Specify either 'containerImage' or 'containerFile' attribute")
        if( req.containerImage && req.containerFile )
            throw new BadRequestException("Attributes 'containerImage' and 'containerFile' cannot be used in the same request")
        if( req.containerImage?.contains('@sha256:') && req.containerConfig && !req.freeze )
            throw new BadRequestException("Container requests made using a SHA256 as tag does not support the 'containerConfig' attribute")
        if( req.freeze && !req.buildRepository && !buildConfig.defaultPublicRepository )
            throw new BadRequestException("When freeze mode is enabled the target build repository must be specified - see 'wave.build.repository' setting")
        if( req.formatSingularity() && !req.freeze )
            throw new BadRequestException("Singularity build is only allowed enabling freeze mode - see 'wave.freeze' setting")

        // expand inclusions
        inclusionService.addContainerInclusions(req, identity)

        // when 'freeze' is enabled, rewrite the request so that the container configuration specified
        // in the request is included in the build container file instead of being processed via the augmentation process
        if( req.freeze ) {
            req = freezeService.freezeBuildRequest(req, identity)
        }

        String targetImage
        String targetContent
        String condaContent
        String buildId
        boolean buildNew
        if( req.containerFile ) {
            final build = buildRequest(req, identity, ip)
            targetImage = build.targetImage
            targetContent = req.containerFile
            condaContent = req.condaFile
            buildId = build.id
            buildNew = !build.cached
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
                identity,
                targetImage,
                targetContent,
                req.containerConfig,
                condaContent,
                ContainerPlatform.of(req.containerPlatform),
                buildId,
                buildNew,
                req.freeze )
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

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Delete('/container-token/{token}')
    HttpResponse deleteContainerRequest(String token) {
        final record = tokenService.evictRequest(token)
        if( !record ){
            throw new NotFoundException("Missing container record for token: $token")
        }
        return HttpResponse.ok()
    }

    void validateContainerRequest(SubmitContainerTokenRequest req) throws BadRequestException{
        if( req.towerEndpoint && req.towerAccessToken ) {
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

    @Error(exception = AuthorizationException.class)
    HttpResponse<?> handleAuthorizationException() {
        return HttpResponse.unauthorized()
                .header(WWW_AUTHENTICATE, "Basic realm=Wave Authentication")
    }

    @Post('/v1alpha2/container')
    CompletableFuture<HttpResponse<SubmitContainerTokenResponse>> getTokenV2(HttpRequest httpRequest, SubmitContainerTokenRequest req) {
        return getTokenImpl(httpRequest, req, true)
    }

}
