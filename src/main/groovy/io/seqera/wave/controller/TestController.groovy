package io.seqera.wave.controller

import java.nio.file.Path
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.seqera.wave.auth.DockerAuthService
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.UserService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 * Just for testing
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Controller("/")
@CompileStatic
class TestController {

    @Inject
    ContainerBuildService builderService

    @Value('${wave.build.workspace}')
    String workspace

    @Value('${wave.build.repo}')
    String defaultBuildRepo

    @Value('${wave.build.cache}')
    String defaultCacheRepo

    @Inject
    @Value('${wave.allowAnonymous}')
    Boolean allowAnonymous

    @Inject
    UserService userService

    @Inject
    DockerAuthService dockerAuthService

    @Inject HttpClientAddressResolver addressResolver

    @Get('/test-build')
    HttpResponse<String> testBuild(@Nullable String platform,
                                   @Nullable String repo,
                                   @Nullable String cache,
                                   @Nullable String accessToken,
                                   @Nullable Long workspaceId,
                                    HttpRequest httpRequest) {
        if( !accessToken && !allowAnonymous )
            throw new BadRequestException("Missing user access token")

        final User user = accessToken
                ? userService.getUserByAccessToken(accessToken)
                : null
        if( accessToken && !user )
            throw new BadRequestException("Cannot find user for given access token")

        final String dockerFile = """\
            FROM quay.io/nextflow/bash
            RUN echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
            ENV NOW=${System.currentTimeMillis()}
            """

        final ip = addressResolver.resolve(httpRequest)
        final buildRepo = repo ?: defaultBuildRepo
        final cacheRepo = cache ?: defaultCacheRepo
        final configJson = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, cacheRepo, user?.id, workspaceId)

        final req =  new BuildRequest( dockerFile,
                Path.of(workspace),
                buildRepo,
                null,
                user,
                ContainerPlatform.of(platform),
                configJson,
                cacheRepo,
                ip)

        final resp = builderService.buildImage(req)
        return HttpResponse.ok(resp)
    }

}
