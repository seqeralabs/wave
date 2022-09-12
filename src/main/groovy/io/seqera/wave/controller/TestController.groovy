package io.seqera.wave.controller


import java.nio.file.Path
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.ratelimit.RateLimiterService
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
    String buildRepo

    @Value('${wave.build.cache}')
    String cacheRepo

    @Inject
    @Value('${wave.allowAnonymous}')
    Boolean allowAnonymous


    @Inject
    UserService userService

    @Inject @Nullable
    RateLimiterService rateLimiterService

    @Get('/test-build')
    HttpResponse<String> testBuild(@Nullable String platform, @Nullable String repo, @Nullable String cache, @Nullable String accessToken) {
        if( !accessToken && !allowAnonymous )
            throw new BadRequestException("Missing user access token")

        final User user = accessToken
                ? userService.getUserByAccessToken(accessToken)
                : null
        if( accessToken && !user )
            throw new BadRequestException("Cannot find user for given access token")

        if( rateLimiterService ) {
            final key = user?.id?.toString() ?: 'anonymous'
            rateLimiterService.acquireBuild(key)
        }

        final String dockerFile = """\
            FROM quay.io/nextflow/bash
            RUN echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
            ENV NOW=${System.currentTimeMillis()}
            """

        final req =  new BuildRequest( dockerFile,
                Path.of(workspace),
                repo ?: buildRepo,
                null,
                user,
                ContainerPlatform.of(platform),
                cache ?: cacheRepo )
        final resp = builderService.buildImage(req)
        return HttpResponse.ok(resp)
    }

}
