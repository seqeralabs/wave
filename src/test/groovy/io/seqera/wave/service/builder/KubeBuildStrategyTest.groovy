/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.k8s.K8sServiceImpl
import io.seqera.wave.tower.User
import jakarta.inject.Inject
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
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'

        when:
        def req = new BuildRequest('from foo', PATH, repo, null, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{}', cache, null, "", null)
        Files.createDirectories(req.workDir)

        def resp = strategy.build(req)
        then:
        resp
        and:
        1 * k8sService.buildContainer(_, _, _, _, _, _, [service:'wave-build']) >> null

        when:
        def req2 = new BuildRequest('from foo', PATH, repo, null, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('arm64'),'{}', cache, null, "", null)
        Files.createDirectories(req2.workDir)

        def resp2 = strategy.build(req2)
        then:
        resp2
        and:
        1 * k8sService.buildContainer(_, _, _, _, _, _, [service:'wave-build-arm64']) >> null

    }

    def "should get the correct image for a specific architecture"(){
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'

        when:'getting docker with amd64 arch in build request'
        def req = new BuildRequest('from foo', PATH, repo, null, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{}', cache, null, "", null)

        then: 'should return kaniko image'
        strategy.getBuildImage(req) == 'gcr.io/kaniko-project/executor:v1.19.2'

        when:'getting singularity with amd64 arch in build request'
        req = new BuildRequest('from foo', PATH, repo, null, null, null, BuildFormat.SINGULARITY, USER, null, null, ContainerPlatform.of('amd64'),'{}', cache, null, "", null)

        then:'should return singularity amd64 image'
        strategy.getBuildImage(req) == 'quay.io/singularity/singularity:v3.11.4-slim'

        when:'getting singularity with arm64 arch in build request'
        req = new BuildRequest('from foo', PATH, repo, null, null, null, BuildFormat.SINGULARITY, USER, null, null, ContainerPlatform.of('arm64'),'{}', cache, null, "", null)

        then:'should return singularity arm64 image'
        strategy.getBuildImage(req) == 'quay.io/singularity/singularity:v3.11.4-slim-arm64'
    }
}
