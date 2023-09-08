/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.ratelimit

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

import io.micronaut.context.ApplicationContext
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildServiceImpl
import io.seqera.wave.tower.User
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class BuildServiceRateLimitTest extends Specification{

    @Shared
    String buildRepo = 'quay.io/repo/name'

    @Shared
    String cacheRepo = 'quay.io/cache/name'

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
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cacheRepo, null, "127.0.0.1", null)

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
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cacheRepo, null, "127.0.0.1", null)

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
