package io.seqera.wave.service

import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.auth.DockerAuthService
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.tower.User
import io.seqera.wave.util.DataTimeUtils
import io.seqera.wave.util.DigestFunctions
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements the logic to handle Wave containers requests
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerRequestServiceImpl implements ContainerRequestService {

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Inject
    @Value('${wave.allowAnonymous}')
    private Boolean allowAnonymous

    /**
     * The registry repository where the build image will be stored
     */
    @Value('${wave.build.repo}')
    private String defaultBuildRepo

    @Value('${wave.build.cache}')
    private String defaultCacheRepo

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    private String workspace

    @Inject
    private ContainerBuildService buildService

    @Inject
    private DockerAuthService dockerAuthService

    @Inject
    private RegistryProxyService registryProxyService

    @Inject PersistenceService persistenceService

    @PostConstruct
    private void init() {
        log.info "Wave server url: $serverUrl; allowAnonymous: $allowAnonymous"
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
        final condaRecipe = condaContent && !condaContent.contains('@EXPLICIT') ? condaContent : null as String
        final platform = ContainerPlatform.of(req.containerPlatform)
        final buildRepo = req.buildRepository ?: defaultBuildRepo
        final cacheRepo = req.cacheRepository ?: defaultCacheRepo
        final configJson = dockerAuthService.credentialsConfigJson(dockerContent, buildRepo, cacheRepo, user?.id, req.towerWorkspaceId)
        final offset = DataTimeUtils.offsetId(req.timestamp) ?: OffsetDateTime.now().offset.id
        final buildId = computeBuildChecksum(dockerContent, condaRecipe, platform, buildRepo)
        final condaId = computeCondaChecksum(condaRecipe)
        final condaLock = condaId ? persistenceService.loadConda(condaId)?.lockFile : ( condaContent?.contains('@EXPLICIT') ? condaContent : null )
        final startTime = Instant.now()
        // create a unique digest to identify the request
        return BuildRequest.builder()
                .withId( buildId )
                .withDockerFile( dockerContent )
                .withCondaFile( condaRecipe )
                .withCondaId( condaId )
                .withCondaLock( condaLock )
                .withTargetImage( "$buildRepo:$buildId" )
                .withUser( user )
                .withPlatform( platform )
                .withConfigJson( configJson )
                .withCacheRepository( cacheRepo )
                .withWorkDir( Path.of(workspace).resolve(buildId).toAbsolutePath() )
                .withOffsetId( offset )
                .withStartTime( Instant.now() )
                .withJobId( "${buildId}-${startTime.toEpochMilli().toString().md5()[-5..-1]}" )
                .withRequestIp( ip )
                .build()

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

    @Override
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

    static String computeBuildChecksum(String containerFile, String condaFile, ContainerPlatform platform, String repository) {
        final attrs = new LinkedHashMap<String,Object>(10)
        attrs.containerFile = containerFile
        attrs.condaFile = condaFile
        attrs.platform =  platform
        attrs.repository = repository
        return DigestFunctions.md5(attrs)
    }

    static String computeCondaChecksum(String condaFile) {
        if( !condaFile )
            return null
        final attrs = new LinkedHashMap<String,Object>(10)
        attrs.condaFile = condaFile.trim().stripIndent()
        return DigestFunctions.md5(attrs)
    }
}
