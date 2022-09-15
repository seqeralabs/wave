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
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.ratelimit.AcquireRequest
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

    @Inject HttpClientAddressResolver addressResolver

    @Get('/test-build')
    HttpResponse<String> testBuild(@Nullable String platform, @Nullable String repo, @Nullable String cache, @Nullable String accessToken,
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

        final req =  new BuildRequest( dockerFile,
                workspace,
                repo ?: buildRepo,
                null,
                user.id, user.email,
                ContainerPlatform.of(platform),
                cache ?: cacheRepo,
                ip)

        final resp = builderService.buildImage(req)
        return HttpResponse.ok(resp)
    }

}
