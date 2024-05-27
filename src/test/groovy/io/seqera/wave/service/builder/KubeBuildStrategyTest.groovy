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
import java.nio.file.Paths
import java.time.OffsetDateTime

import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BuildTimeoutException
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

        def pod = new V1Pod(metadata: [name: 'podName', creationTimestamp: OffsetDateTime.now()])
        pod.status = new V1PodStatus(phase: "Succeeded")
        def podList = new V1PodList(items: [pod])

        k8sService.buildJob(_, _, _, _, _, _, [service:'wave-build']) >> new V1Job(metadata: [name: 'jobName'])
        k8sService.waitJob(_, _) >> podList
        k8sService.getPod(_) >> pod
        k8sService.waitPod(_, _, _) >> new V1ContainerStateTerminated().exitCode(0)
        k8sService.logsPod(_, _) >> 'stdout'

        when:
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, null, ContainerPlatform.of('amd64'), repo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, repo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.DOCKER).withBuildId('1')
        Files.createDirectories(req.workDir)

        def resp = strategy.build(req)

        then:
        resp

        when:
        def req2 = new BuildRequest(containerId, dockerfile, null, null, PATH, targetImage, USER, ContainerPlatform.of('arm64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.DOCKER).withBuildId('1')
        Files.createDirectories(req2.workDir)

        def resp2 = strategy.build(req2)
        then:
        resp2
        and:
        1 * k8sService.buildJob(_, _, _, _, _, _, [service:'wave-build-arm64']) >> null

    }

    def "should get the correct image for a specific architecture"(){
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def dockerfile = 'from foo'

        when:'getting docker with amd64 arch in build request'
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, null, ContainerPlatform.of('amd64'), repo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, repo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{"config":"json"}', null,null , null, null, BuildFormat.DOCKER).withBuildId('1')

        then: 'should return buildkit image'
        strategy.getBuildImage(req) == 'moby/buildkit:v0.13.2-rootless'

        when:'getting singularity with amd64 arch in build request'
        req = new BuildRequest(containerId, dockerfile, null, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.SINGULARITY).withBuildId('1')

        then:'should return singularity amd64 image'
        strategy.getBuildImage(req) == 'quay.io/singularity/singularity:v3.11.4-slim'

        when:'getting singularity with arm64 arch in build request'
        req = new BuildRequest(containerId, dockerfile, null, null, PATH, targetImage, USER, ContainerPlatform.of('arm64'), cache, "10.20.30.40", '{}', null,null , null, null, BuildFormat.SINGULARITY).withBuildId('1')

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
        def containerId = ContainerHelper.makeContainerId(dockerfile, null, null, ContainerPlatform.of('amd64'), repo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, repo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerfile, null, null, PATH, targetImage, USER, ContainerPlatform.of('amd64'), cache, "10.20.30.40", '{"config":"json"}', null,null , null, null, BuildFormat.DOCKER).withBuildId('1')

        when:
        def jobName = strategy.getName(req)

        then:
        req.buildId == '143ee73bcdac45b1_1'
        jobName == 'build-143ee73bcdac45b1-1'
    }

    def "should launch k8s build container when building singularity image"() {
        given:
        def name = "pod-name"
        def buildImage = "singularity-builder"
        def buildCmd = ["cmd1", "cmd2"]
        def req = Mock(BuildRequest) {
            formatSingularity() >> true
            getWorkDir() >> Paths.get("/work/dir")
            getBuildId() >> "build-123"
        }
        def configFile = Paths.get("/config/file")
        def spackConfig = Mock(SpackConfig)
        def nodeSelector = ["key": "value"]

        when:
        strategy.launchContainerBuild(name, buildImage, buildCmd, req, configFile, spackConfig, nodeSelector)

        then:
        1 * k8sService.buildContainer(name, buildImage, buildCmd, req.getWorkDir(), configFile, spackConfig, nodeSelector)
    }

    def "should throw BuildTimeoutException when no pods are returned"() {
        given:
        def name = "job-name"
        def buildImage = "docker-builder"
        def buildCmd = ["cmd1", "cmd2"]
        def req = Mock(BuildRequest) {
            formatDocker() >> true
            getWorkDir() >> Paths.get("/work/dir")
            getBuildId() >> "build-123"
        }
        def configFile = Paths.get("/config/file")
        def spackConfig = Mock(SpackConfig)
        def nodeSelector = ["key": "value"]

        when:
        strategy.launchContainerBuild(name, buildImage, buildCmd, req, configFile, spackConfig, nodeSelector)

        then:
        1 * k8sService.buildJob(name, buildImage, buildCmd, req.getWorkDir(), configFile, spackConfig, nodeSelector) >> Mock(V1Job)
        1 * k8sService.waitJob(_, _) >> Mock(V1PodList) {
            getItems() >> []
        }
        thrown(BuildTimeoutException)
    }
}
