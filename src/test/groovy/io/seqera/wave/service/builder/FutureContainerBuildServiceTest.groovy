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

import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.impl.ContainerBuildServiceImpl
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.ContainerHelper
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.ia>
 */
class FutureContainerBuildServiceTest extends Specification {

    String buildRepo = 'build/repo'
    String cacheRepo = 'cache/repo'

    def 'should wait for successful container build' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerfile = """
        FROM busybox
        RUN echo 'hello' > hello.txt
        """.stripIndent()
        and:
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", '{"config":"json"}', null,null , null, null, BuildFormat.DOCKER, Duration.ofMinutes(1)).withBuildId('1')
        def res = new BuildResult("", 0, "a fake build result in a test", Instant.now(), Duration.ofSeconds(3), 'abc')
        and:
        def buildStore = Mock(BuildStateStore)
        def buildCounter = Mock(BuildCounterStore)
        buildStore.getBuildResult(targetImage) >> res
        buildStore.awaitBuild(targetImage) >> CompletableFuture.completedFuture(res)
        def service = new ContainerBuildServiceImpl(buildStore: buildStore, buildCounter: buildCounter)

        when:
        service.checkOrSubmit(req)
        then:
        noExceptionThrown()

        when:
        def status = service.buildResult(req.targetImage).get()
        then:
        status.getExitStatus() == 0

        cleanup:
        folder?.deleteDir()
    }


    def 'should wait for unsuccessful container build' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerfile = """
        FROM busybox
        RUN echo 'hello' > hello.txt
        """.stripIndent()
        and:
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", '{"config":"json"}', null,null , null, null, BuildFormat.DOCKER, Duration.ofMinutes(1)).withBuildId('1')
        def res = new BuildResult("", 1, "a fake build result in a test", Instant.now(), Duration.ofSeconds(3), 'abc')
        and:
        def buildStore = Mock(BuildStateStore)
        def buildCounter = Mock(BuildCounterStore)
        buildStore.getBuildResult(targetImage) >> res
        buildStore.awaitBuild(targetImage) >> CompletableFuture.completedFuture(res)
        def service = new ContainerBuildServiceImpl(buildStore: buildStore, buildCounter: buildCounter)

        when:
        service.checkOrSubmit(req)
        then:
        noExceptionThrown()

        when:
        def status = service.buildResult(req.targetImage).get()
        then:
        status.getExitStatus() == 1

        cleanup:
        folder?.deleteDir()
    }

}
