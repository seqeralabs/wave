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

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.k8s.K8sServiceImpl
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import io.seqera.wave.util.ContainerHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
@Property(name="wave.build.workspace",value="/build/work")
@Property(name="wave.build.k8s.namespace",value="foo")
@Property(name="wave.build.k8s.configPath",value="/home/kube.config")
@Property(name="wave.build.k8s.storage.claimName",value="bar")
@Property(name="wave.build.k8s.storage.mountPath",value="/build")
@Property(name='wave.build.k8s.node-selector[linux/amd64]',value="service=wave-build")
@Property(name='wave.build.k8s.node-selector[linux/arm64]',value="service=wave-build-arm64")
class KubeBuildStrategyTest extends Specification {

    @Inject
    KubeBuildStrategy strategy

    @Inject
    K8sService k8sService

    @MockBean(K8sServiceImpl)
    K8sService k8sService(){
        Mock(K8sService)
    }


    def "request to build a container with right selector"(){
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def dockerfile = 'from foo'

        when:
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, ContainerPlatform.of('amd64'), repo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, repo, containerId, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.DOCKER, Duration.ofMinutes(1)).withBuildId('1')
        Files.createDirectories(req.workDir)

        def resp = strategy.build(req)
        then:
        resp
        and:
        1 * k8sService.buildContainer(_, _, _, _, _, _, _, [service:'wave-build']) >> null

        when:
        def req2 = new BuildRequest(containerId, dockerfile, null, PATH, targetImage, USER, ContainerPlatform.of('arm64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.DOCKER, Duration.ofMinutes(1)).withBuildId('1')
        Files.createDirectories(req2.workDir)

        def resp2 = strategy.build(req2)
        then:
        resp2
        and:
        1 * k8sService.buildContainer(_, _, _, _, _, _, _, [service:'wave-build-arm64']) >> null

    }

    def "should get the correct image for a specific architecture"(){
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def dockerfile = 'from foo'

        when:'getting docker with amd64 arch in build request'
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, ContainerPlatform.of('amd64'), repo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, repo, containerId, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{"config":"json"}', null,null , null, null, BuildFormat.DOCKER, Duration.ofMinutes(1)).withBuildId('1')

        then: 'should return buildkit image'
        strategy.getBuildImage(req) == 'moby/buildkit:v0.14.1-rootless'

        when:'getting singularity with amd64 arch in build request'
        req = new BuildRequest(containerId, dockerfile, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.SINGULARITY,Duration.ofMinutes(1)).withBuildId('1')

        then:'should return singularity amd64 image'
        strategy.getBuildImage(req) == 'quay.io/singularity/singularity:v3.11.4-slim'

        when:'getting singularity with arm64 arch in build request'
        req = new BuildRequest(containerId, dockerfile, null, PATH, targetImage, USER, ContainerPlatform.of('arm64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.SINGULARITY, Duration.ofMinutes(1)).withBuildId('1')

        then:'should return singularity arm64 image'
        strategy.getBuildImage(req) == 'quay.io/singularity/singularity:v3.11.4-slim-arm64'
    }

    def 'should get correct pod name for build' () {
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def dockerfile = 'from foo'
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, ContainerPlatform.of('amd64'), repo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, repo, containerId, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{"config":"json"}', null,null , null, null, BuildFormat.DOCKER, Duration.ofMinutes(1)).withBuildId('1')

        when:
        def podName = strategy.podName(req)

        then:
        req.buildId == '143ee73bcdac45b1_1'
        podName == 'build-143ee73bcdac45b1-1'
    }
}
