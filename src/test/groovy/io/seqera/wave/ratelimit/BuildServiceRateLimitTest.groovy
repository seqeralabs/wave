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
import io.seqera.wave.tower.PlatformId
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


    def 'should not allow more auth builds than rate limit' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerfile = """
        FROM busybox
        RUN echo hi > hello.txt
        """.stripIndent()
        and:
        def ID = BuildRequest.computeDigest(dockerfile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def TARGET = BuildRequest.makeTarget(BuildFormat.DOCKER, buildRepo, ID, null, null)
        def REQ = new BuildRequest(ID, dockerfile, null, null, folder, TARGET, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "127.0.0.1", '{"config":"json"}', null, null, null, null,BuildFormat.DOCKER)

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
        def ID = BuildRequest.computeDigest(dockerfile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def TARGET = BuildRequest.makeTarget(BuildFormat.DOCKER, buildRepo, ID, null, null)
        def REQ = new BuildRequest(ID, dockerfile, null, null, folder, TARGET, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "127.0.0.1", '{"config":"json"}', null, null, null, null,BuildFormat.DOCKER)

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
