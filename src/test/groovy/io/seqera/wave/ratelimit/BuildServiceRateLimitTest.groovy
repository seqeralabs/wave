package io.seqera.wave.ratelimit

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.tower.User


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class BuildServiceRateLimitTest extends Specification{
    @Value('${wave.build.repo}') String buildRepo
    @Value('${wave.build.cache}') String cacheRepo

    @Shared
    ApplicationContext applicationContext

    @Shared
    ContainerBuildServiceImpl containerBuildService

    @Shared
    RateLimiterConfig configuration

    def setupSpec() {
        applicationContext = ApplicationContext.run('test', 'rate-limit')
        containerBuildService = applicationContext.getBean(ContainerBuildServiceImpl)
        configuration = applicationContext.getBean(RateLimiterConfig)
    }

    def mockUser = Mock(User){
        getId() >> 1234
    }

    def 'should not allow more auth builds than rate limit' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerfile = """
        FROM busybox
        RUN echo hi > hello.txt
        """.stripIndent()
        and:
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, mockUser, ContainerPlatform.of('amd64'), cacheRepo,"127.0.0.1")

        when:
        (0..configuration.build.authenticated.max).each {
            containerBuildService.launchAsync(REQ)
        }
        then:
        thrown(SlowDownException)

        cleanup:
        folder?.deleteDir()
    }

    def 'should not allow more anonymous builds than rate limit' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerfile = """
        FROM busybox
        RUN echo hi > hello.txt
        """.stripIndent()
        and:
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, Mock(User), ContainerPlatform.of('amd64'), cacheRepo,"127.0.0.1")

        when:
        (0..configuration.build.anonymous.max).each {
            containerBuildService.launchAsync(REQ)
        }
        then:
        thrown(SlowDownException)

        cleanup:
        folder?.deleteDir()
    }
}
