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

package io.seqera.wave.service.k8s

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration

import io.kubernetes.client.custom.Quantity
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class K8sServiceImplTest extends Specification {

    def 'should validate context OK ' () {
        when:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        ctx.getBean(K8sServiceImpl)
        then:
        noExceptionThrown()
        and:
        ctx.close()

        // no storage is setting are provided
        when:
        PROPS = [
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config' ]
        and:
        ctx = ApplicationContext.run(PROPS)
        ctx.getBean(K8sServiceImpl)
        then:
        noExceptionThrown()
        and:
        ctx.close()
    }

    def 'should get mount path' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.mountBuildStorage(Path.of('/foo'), '/foo', true)
        then:
        result.name == 'build-data'
        result.mountPath == '/foo'
        result.subPath == null
        result.readOnly

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/'), '/foo', true)
        then:
        result.name == 'build-data'
        result.mountPath == '/foo'
        result.subPath == null
        result.readOnly

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/work/x1'), '/foo', true)
        then:
        result.name == 'build-data'
        result.mountPath == '/foo/work/x1'
        result.subPath == 'work/x1'
        result.readOnly

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/work/x1'), null, false)
        then:
        result.name == 'build-data'
        result.mountPath == '/foo/work/x1'
        !result.readOnly

        cleanup:
        ctx.close()
    }

    def 'should create build vol' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.volumeBuildStorage('/foo/work/x1', null)
        then:
        result.name == 'build-data'
        result.hostPath.path == '/foo/work/x1'

        when:
        result = k8sService.volumeBuildStorage('/foo/work/x1', 'foo')
        then:
        result.name == 'build-data'
        result.persistentVolumeClaim.claimName == 'foo'

        cleanup:
        ctx.close()
    }

    def 'should get docker config vol' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def mount = k8sService.mountHostPath(Path.of('/foo/work/x1/config.json'), '/foo','/kaniko/.docker/config.json')
        then:
        mount.name == 'build-data'
        mount.mountPath == '/kaniko/.docker/config.json'
        mount.readOnly
        mount.subPath == 'work/x1/config.json'

        cleanup:
        ctx.close()
    }

    def 'should get spack dir vol' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def mount = k8sService.mountSpackCacheDir(Path.of('/foo/work/x1'), '/foo', '/opt/spack/cache')
        then:
        mount.name == 'build-data'
        mount.mountPath == '/opt/spack/cache'
        mount.subPath == 'work/x1'
        !mount.readOnly

        cleanup:
        ctx.close()
    }

    def 'should create build pod for kaniko' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.timeout': '10s',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.toleration.arm64.key': 'arch',
                'wave.build.k8s.toleration.arm64.value': 'arm64']
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), Path.of('/build/work/xyz/config.json'), null, [:], ContainerPlatform.of('arm64'))
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        result.spec.containers.get(0).name == 'foo'
        result.spec.containers.get(0).image == 'my-image:latest'
        result.spec.containers.get(0).args ==  ['this','that']
        result.spec.containers.get(0).command == null
        and:
        result.spec.containers.get(0).volumeMounts.size() == 2
        and:
        result.spec.containers.get(0).volumeMounts.get(0).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(0).mountPath == '/kaniko/.docker/config.json'
        result.spec.containers.get(0).volumeMounts.get(0).subPath == 'work/xyz/config.json'
        and:
        result.spec.containers.get(0).volumeMounts.get(1).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(1).mountPath == '/build/work/xyz'
        result.spec.containers.get(0).volumeMounts.get(1).subPath == 'work/xyz'

        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'

        and: 'should set the toleration for arm64'
        result.spec.tolerations.get(0).key == 'arch'
        result.spec.tolerations.get(0).value == 'arm64'
        result.spec.tolerations.get(0).operator == 'Equal'
        result.spec.tolerations.get(0).effect == 'NoSchedule'
        cleanup:
        ctx.close()
    }

    def 'should create build pod for singularity' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.timeout': '10s',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def workDir = Path.of('/build/work/xyz')
        when:
        def result = k8sService.buildSpec('foo', 'singularity:latest', ['this','that'], workDir, workDir.resolve('config.json'), null, [:], ContainerPlatform.of('amd64'))
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        result.spec.containers.get(0).name == 'foo'
        result.spec.containers.get(0).image == 'singularity:latest'
        result.spec.containers.get(0).command ==  ['this','that']
        result.spec.containers.get(0).args == null
        and:
        result.spec.containers.get(0).volumeMounts.size() == 3
        and:
        result.spec.containers.get(0).volumeMounts.get(0).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(0).mountPath == '/root/.singularity/docker-config.json'
        result.spec.containers.get(0).volumeMounts.get(0).subPath == 'work/xyz/config.json'
        and:
        result.spec.containers.get(0).volumeMounts.get(1).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(1).mountPath == '/root/.singularity/remote.yaml'
        result.spec.containers.get(0).volumeMounts.get(1).subPath == 'work/xyz/singularity-remote.yaml'
        and:
        result.spec.containers.get(0).volumeMounts.get(2).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(2).mountPath == '/build/work/xyz'
        result.spec.containers.get(0).volumeMounts.get(2).subPath == 'work/xyz'
        and:
        result.spec.containers.get(0).getWorkingDir() == null
        result.spec.containers.get(0).getSecurityContext().privileged

        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'

        cleanup:
        ctx.close()
    }

    def 'should create build pod with spack cache' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.timeout': '10s',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.spack.secretKeyFile':'/build/host/spack/key',
                'wave.build.spack.secretMountPath':'/opt/container/spack/key'
        ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def spackConfig = ctx.getBean(SpackConfig)
        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, spackConfig, [:], ContainerPlatform.of('arm64'))
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        result.spec.containers.get(0).name == 'foo'
        result.spec.containers.get(0).image == 'my-image:latest'
        result.spec.containers.get(0).args ==  ['this','that']
        and:
        result.spec.containers.get(0).volumeMounts.size() == 2
        and:
        result.spec.containers.get(0).volumeMounts.get(0).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(0).mountPath == '/build/work/xyz'
        result.spec.containers.get(0).volumeMounts.get(0).subPath == 'work/xyz'
        and:
        result.spec.containers.get(0).volumeMounts.get(1).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(1).mountPath == '/opt/container/spack/key'
        result.spec.containers.get(0).volumeMounts.get(1).subPath == 'host/spack/key'
        result.spec.containers.get(0).volumeMounts.get(1).readOnly

        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'

        cleanup:
        ctx.close()
    }

    def 'should create build pod without init container' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.timeout': '10s',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null,[:], ContainerPlatform.of('arm64'))
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        !result.spec.initContainers
        and:
        result.spec.containers.get(0).name == 'foo'
        result.spec.containers.get(0).image == 'my-image:latest'
        result.spec.containers.get(0).args ==  ['this','that']
        and:
        result.spec.containers.get(0).volumeMounts.size() == 1
        and:
        result.spec.containers.get(0).volumeMounts.get(0).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(0).mountPath == '/build/work/xyz'
        result.spec.containers.get(0).volumeMounts.get(0).subPath == 'work/xyz'

        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'


        cleanup:
        ctx.close()
    }

    def 'should add labels ' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.labels': ['department': 'unit a','organization': 'org']
        ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null,[:], null)
        then:
        result.metadata.name == 'foo'
        result.metadata.labels.toString() == PROPS['wave.build.k8s.labels'].toString()
        and:
        ctx.close()
    }

    def 'should add nodeselector' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.node-selector': [
                        'linux/amd64': 'service=wave-build',
                        'linux/arm64': 'service=wave-build-arm64'
                ],
                'wave.build.k8s.resources.requests.cpu': '2',
                'wave.build.k8s.resources.requests.memory': '4Gi',
        ] as Map<String,Object>
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null, PROPS['wave.build.k8s.node-selector'] as Map<String,String>, ContainerPlatform.of('arm64'))
        then:
        result.spec.nodeSelector.toString() == PROPS['wave.build.k8s.node-selector'].toString()
        and:
        result.spec.getContainers().get(0).getResources().getRequests().get('cpu') == new Quantity('2')
        result.spec.getContainers().get(0).getResources().getRequests().get('memory') == new Quantity('4Gi')
        and:
        ctx.close()
    }

    def 'should add serviceAccount' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.service-account': 'theAdminAccount'
        ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null,[:], ContainerPlatform.of('arm64'))
        then:
        result.spec.serviceAccount == PROPS['wave.build.k8s.service-account']
        and:
        ctx.close()
    }

    def 'should create scan pod' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build', ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def config = Mock(ScanConfig) {
            getCacheDirectory() >> Path.of('/build/work/.trivy')
            getTimeout() >> Duration.ofSeconds(10)
        }

        when:
        def result = k8sService.scanSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), Path.of('/build/work/xyz/config.json'), config, null, ContainerPlatform.of('arm64') )
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        result.spec.containers.get(0).name == 'foo'
        result.spec.containers.get(0).image == 'my-image:latest'
        result.spec.containers.get(0).args ==  ['this','that']
        and:
        result.spec.containers.get(0).volumeMounts.size() == 3
        and:
        result.spec.containers.get(0).volumeMounts.get(0).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(0).mountPath == '/root/.docker/config.json'
        result.spec.containers.get(0).volumeMounts.get(0).subPath == 'work/xyz/config.json'
        and:
        result.spec.containers.get(0).volumeMounts.get(1).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(1).mountPath == '/build/work/xyz'
        result.spec.containers.get(0).volumeMounts.get(1).subPath == 'work/xyz'
        and:
        result.spec.containers.get(0).volumeMounts.get(2).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(2).mountPath == '/root/.cache/'
        result.spec.containers.get(0).volumeMounts.get(2).subPath == 'work/.trivy'

        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'

        cleanup:
        ctx.close()
    }
}
