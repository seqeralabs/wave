package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.micronaut.scheduling.TaskScheduler
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.mail.MailService
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.APPEND
import static java.nio.file.StandardOpenOption.CREATE

import static io.seqera.wave.util.StringUtils.indent

/**
 * Implements container build service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerBuildServiceImpl implements ContainerBuildService {

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    String workspace

    @Value('${wave.build.debug}')
    @Nullable
    Boolean debugMode

    /**
     * The registry repository where the build image will be stored
     */
    @Value('${wave.build.repo}')
    String buildRepo

    @Value('${wave.build.cache}')
    String cacheRepo

    @Value('${wave.build.image}')
    String buildImage

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    @Inject
    @Nullable
    private MailService mailService

    private final Map<String,BuildRequest> buildRequests = new HashMap<>()

    @Inject
    private RegistryLookupService lookupService

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    @Inject
    TaskScheduler taskScheduler
    private RegistryCredentialsProvider credentialsProvider

    @Inject
    private BuildStrategy buildStrategy

    @Override
    String buildImage(String dockerfileContent, String condaFile, User user) {
        if( !dockerfileContent )
            throw new BadRequestException("Missing dockerfile content")
        // create a unique digest to identify the request
        final request = new BuildRequest(dockerfileContent, Path.of(workspace), buildRepo, condaFile, user)
        return getOrSubmit(request)
    }

    @Override
    BuildStatus isUnderConstruction(String targetImage) {
        final req = buildRequests.get(targetImage)
        if( !req )
            return BuildStatus.UNKNOWN
        final status = req.status
        status
    }


    protected String credsJson(String registry) {
        final info = lookupService.lookup(registry)
        final creds = credentialsProvider.getCredentials(registry)
        if( !creds ) {
            return null
        }
        final encode = "${creds.username}:${creds.password}".getBytes().encodeBase64()
        return """{"auths":{"${info.getHost()}":{"auth":"$encode"}}}"""
    }

    protected BuildResult launch(BuildRequest req) {
        // create the workdir path
        Files.createDirectories(req.workDir)
        // save the dockerfile
        final dockerfile = req.workDir.resolve('Dockerfile')
        Files.write(dockerfile, req.dockerFile.bytes, CREATE, APPEND)
        // save the conda file
        if( req.condaFile ) {
            final condaFile = req.workDir.resolve('conda.yml')
            Files.write(condaFile, req.condaFile.bytes, CREATE, APPEND)
        }
        // create creds file for target repo
        final creds = credsJson(buildRepo)

        // launch an external process to build the container
        try {
            final resp = buildStrategy.build(req, creds)
            log.info "== Build completed with status=$resp.exitStatus; stdout: (see below)\n${indent(resp.logs)}"
            return resp
        }
        catch (Exception e) {
            BuildResult result = new BuildResult(req.id, -1, e.message, req.startTime)
            req.markAsCompleted( BuildStatus.FAILED, result)
            log.error "== Ouch! Unable to build container request=$req", e
            result
        }
        finally {
            if( !debugMode )
                buildStrategy.cleanup(req)
        }
    }

    protected void callLaunch(BuildRequest request) {
        final result = launch(request)
        sendCompletionEmail(request,result)
    }

    protected sendCompletionEmail(BuildRequest request, BuildResult result) {
        try {
            mailService?.sendCompletionMail(result, request.user)
        }
        catch (Exception e) {
            log.warn "Enable to send completion notication - reason: ${e.message?:e}"
        }
    }

    protected String getOrSubmit(BuildRequest request) {
        // use the target image as the cache key
        synchronized (buildRequests) {
            if( !buildRequests.containsKey(request.targetImage) ) {
                log.info "== Submit build request request: $request"
                taskScheduler.schedule(Duration.ofMillis(1), {
                    callLaunch(request)
                })
                buildRequests.put(request.targetImage, request)
            }
            else {
                log.info "== Hit build cache for request: $request"
            }
            return request.targetImage
        }
    }

}
