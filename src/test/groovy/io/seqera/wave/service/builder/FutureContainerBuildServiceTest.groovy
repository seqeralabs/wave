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

package io.seqera.wave.service.builder

import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.time.Duration
import java.time.Instant

import io.micronaut.context.annotation.Value
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.ia>
 */
@MicronautTest
class FutureContainerBuildServiceTest extends Specification {

    @Value('${wave.build.repo}') String buildRepo
    @Value('${wave.build.cache}') String cacheRepo

    @Inject
    ContainerBuildServiceImpl service

    int exitCode

    @MockBean(BuildStrategy)
    BuildStrategy fakeBuildStrategy(){
        new BuildStrategy() {
            @Override
            BuildResult build(BuildRequest req) {
                new BuildResult("", exitCode, "a fake build result in a test", Instant.now(), Duration.ofSeconds(3), 'abc')
            }

            @Override
            String getLogs(String buildId) {
                return "fake build logs"
            }
        }
    }


    @Timeout(30)
    def 'should wait to build container completion' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerfile = """
        FROM busybox
        RUN echo $EXIT_CODE > hello.txt
        """.stripIndent()
        and:
        def req = new BuildRequest(dockerfile, folder, buildRepo, null, null, BuildFormat.DOCKER, Mock(PlatformId), null, null, ContainerPlatform.of('amd64'),'{auth}', cacheRepo, null, "", null).withBuildId('1')

        when:
        exitCode = EXIT_CODE
        service.checkOrSubmit(req)
        then:
        noExceptionThrown()

        when:
        def status = service.buildResult(req.targetImage).get()
        then:
        status.getExitStatus() == EXIT_CODE

        cleanup:
        folder?.deleteDir()

        where:
        EXIT_CODE | _
        0         | _
        1         | _
    }

}
