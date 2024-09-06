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

package io.seqera.wave.service.k8s

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime

import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.configuration.SpackConfig
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class K8sServiceImplTest extends Specification {

    @Replaces(ScanConfig.class)
    static class MockScanConfig extends ScanConfig {
        @Override
        Path getCacheDirectory() {
            return Path.of('/build/scan/cache')
        }
    }

    def 'should validate context OK ' () {
        when:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.scan.enabled': 'true']
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
        def mount = k8sService.mountHostPath(Path.of('/foo/work/x1/config.json'), '/foo','/home/user/.docker/config.json')
        then:
        mount.name == 'build-data'
        mount.mountPath == '/home/user/.docker/config.json'
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

    def 'should create build pod for buildkit' () {
        given:
        def PROPS = [
                'wave.build.workspace'            : '/build/work',
                'wave.build.k8s.namespace'        : 'my-ns',
                'wave.build.k8s.configPath'       : '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build']
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this', 'that'], Path.of('/build/work/xyz'), Path.of('/build/work/xyz/config.json'), Duration.ofSeconds(10), null, [:])
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        verifyAll(result.spec.containers.get(0)) {
            name == 'foo'
            image == 'my-image:latest'
            args == ['this', 'that']
            env.name == ['BUILDKITD_FLAGS']
            env.value == ['--oci-worker-no-process-sandbox']
            command == ['buildctl-daemonless.sh']
            volumeMounts.size() == 2
            volumeMounts.get(0).name == 'build-data'
            volumeMounts.get(0).mountPath == '/home/user/.docker/config.json'
            volumeMounts.get(0).subPath == 'work/xyz/config.json'
            volumeMounts.get(1).name == 'build-data'
            volumeMounts.get(1).mountPath == '/build/work/xyz'
            volumeMounts.get(1).subPath == 'work/xyz'
        }
        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'

        cleanup:
        ctx.close()
    }

    def 'should create build pod for singularity' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def workDir = Path.of('/build/work/xyz')
        when:
        def result = k8sService.buildSpec('foo', 'singularity:latest', ['this','that'], workDir, workDir.resolve('config.json'), Duration.ofSeconds(10), null, [:])
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:

        verifyAll(result.spec.containers.get(0)) {
            name == 'foo'
            image == 'singularity:latest'
            command == ['this', 'that']
            args == null
            volumeMounts.size() == 3
            volumeMounts.get(0).name == 'build-data'
            volumeMounts.get(0).mountPath == '/root/.singularity/docker-config.json'
            volumeMounts.get(0).subPath == 'work/xyz/config.json'
            volumeMounts.get(1).name == 'build-data'
            volumeMounts.get(1).mountPath == '/root/.singularity/remote.yaml'
            volumeMounts.get(1).subPath == 'work/xyz/singularity-remote.yaml'
            volumeMounts.get(2).name == 'build-data'
            volumeMounts.get(2).mountPath == '/build/work/xyz'
            volumeMounts.get(2).subPath == 'work/xyz'
            getWorkingDir() == null
            getSecurityContext().privileged
        }
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null,Duration.ofSeconds(10), spackConfig, [:])
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        verifyAll(result.spec.containers.get(0)) {
            name == 'foo'
            image == 'my-image:latest'
            args == ['this', 'that']
            env.name == ['BUILDKITD_FLAGS']
            env.value == ['--oci-worker-no-process-sandbox']
            volumeMounts.size() == 2
            volumeMounts.get(0).name == 'build-data'
            volumeMounts.get(0).mountPath == '/build/work/xyz'
            volumeMounts.get(0).subPath == 'work/xyz'
            volumeMounts.get(1).name == 'build-data'
            volumeMounts.get(1).mountPath == '/opt/container/spack/key'
            volumeMounts.get(1).subPath == 'host/spack/key'
            volumeMounts.get(1).readOnly
        }
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
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'build-claim',
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, Duration.ofSeconds(10), null,[:])
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        !result.spec.initContainers
        and:
        verifyAll(result.spec.containers.get(0)) {
            name == 'foo'
            image == 'my-image:latest'
            args == ['this', 'that']
            env.name == ['BUILDKITD_FLAGS']
            env.value == ['--oci-worker-no-process-sandbox']
            volumeMounts.size() == 1
            volumeMounts.get(0).name == 'build-data'
            volumeMounts.get(0).mountPath == '/build/work/xyz'
            volumeMounts.get(0).subPath == 'work/xyz'
        }
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, Duration.ofSeconds(10), null,[:])
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, Duration.ofSeconds(10), null, PROPS['wave.build.k8s.node-selector'] as Map<String,String>)
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, Duration.ofSeconds(10), null,[:])
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
        def result = k8sService.scanSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), Path.of('/build/work/xyz/config.json'), config, null )
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.activeDeadlineSeconds == 10
        and:
        verifyAll(result.spec.containers.get(0)) {
            name == 'foo'
            image == 'my-image:latest'
            args == ['this', 'that']
            volumeMounts.size() == 3
            volumeMounts.get(0).name == 'build-data'
            volumeMounts.get(0).mountPath == '/root/.docker/config.json'
            volumeMounts.get(0).subPath == 'work/xyz/config.json'
            volumeMounts.get(1).name == 'build-data'
            volumeMounts.get(1).mountPath == '/build/work/xyz'
            volumeMounts.get(1).subPath == 'work/xyz'
            volumeMounts.get(2).name == 'build-data'
            volumeMounts.get(2).mountPath == '/root/.cache/'
            volumeMounts.get(2).subPath == 'work/.trivy'
        }
        and:
        result.spec.volumes.get(0).name == 'build-data'
        result.spec.volumes.get(0).persistentVolumeClaim.claimName == 'build-claim'

        cleanup:
        ctx.close()
    }

    def 'should create transfer job spec with defaults' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.configPath': '/home/kube.config' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def config = Mock(BlobCacheConfig) {
            getTransferTimeout() >> Duration.ofSeconds(20)
            getEnvironment() >> [:]
            getRetryAttempts() >> 5
            getDeleteAfterFinished() >> Duration.ofDays(10)
        }

        when:
        def result = k8sService.createTransferJobSpec('foo', 'my-image:latest', ['this','that'], config)
        result
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.backoffLimit == 5
        result.spec.ttlSecondsAfterFinished == Duration.ofDays(10).seconds as Integer
        and:
        verifyAll(result.spec.template.spec) {
            activeDeadlineSeconds == 20
            serviceAccount == null
            containers.get(0).name == 'foo'
            containers.get(0).image == 'my-image:latest'
            containers.get(0).args ==  ['this','that']
            !containers.get(0).getEnv()
            !containers.get(0).getResources().limits
            !containers.get(0).getResources().requests
        }
        ctx.close()
    }

    def 'should create transfer job spec with custom settings' () {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'my-ns',
                'wave.build.k8s.service-account': 'foo-sa',
                'wave.build.k8s.configPath': '/home/kube.config' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def config = Mock(BlobCacheConfig) {
            getTransferTimeout() >> Duration.ofSeconds(20)
            getEnvironment() >> ['FOO':'one', 'BAR':'two']
            getRequestsCpu() >> '2'
            getRequestsMemory() >> '8Gi'
            getRetryAttempts() >> 3
            getDeleteAfterFinished() >> Duration.ofDays(1)
        }

        when:
        def result = k8sService.createTransferJobSpec('foo', 'my-image:latest', ['this','that'], config)
        then:
        result.metadata.name == 'foo'
        result.metadata.namespace == 'my-ns'
        and:
        result.spec.backoffLimit == 3
        result.spec.ttlSecondsAfterFinished == Duration.ofDays(1).seconds as Integer
        and:
        verifyAll(result.spec.template.spec) {
            activeDeadlineSeconds == 20
            serviceAccount == 'foo-sa'
            containers.get(0).name == 'foo'
            containers.get(0).image == 'my-image:latest'
            containers.get(0).args ==  ['this','that']
            containers.get(0).getEnv().get(0) == new V1EnvVar().name('FOO').value('one')
            containers.get(0).getEnv().get(1) == new V1EnvVar().name('BAR').value('two')
            containers.get(0).getResources().requests.get('cpu') == new Quantity('2')
            containers.get(0).getResources().requests.get('memory') == new Quantity('8Gi')
            !containers.get(0).getResources().limits
        }

        cleanup:
        ctx.close()
    }

    def "deletePodWhenReachStatus should delete pod when status is reached within timeout"() {
        given:
        def podName = "test-pod"
        def statusName = "Succeeded"
        def timeout = 5000
        def api = Mock(CoreV1Api)
        api.readNamespacedPod(_,_,_) >> new V1Pod(status: new V1PodStatus(phase: statusName))
        def k8sClient = new K8sClient() {
            @Override
            ApiClient apiClient() {
                    return null
            }
            CoreV1Api coreV1Api() {
                return api
            }
        }

        def k8sService = new K8sServiceImpl(k8sClient: k8sClient)

        when:
        k8sService.deletePodWhenReachStatus(podName, statusName, timeout)

        then:
        1 * api.deleteNamespacedPod('test-pod', null, null, null, null, null, null, null)
    }

    def "deletePodWhenReachStatus should not delete pod if status is not reached within timeout"() {
        given:
        def podName = "test-pod"
        def statusName = "Succeeded"
        def timeout = 5000
        def api = Mock(CoreV1Api)
        api.readNamespacedPod(_,_,_) >> new V1Pod(status: new V1PodStatus(phase: "Running"))
        def k8sClient = new K8sClient() {
            @Override
            ApiClient apiClient() {
                return null
            }
            CoreV1Api coreV1Api() {
                return api
            }
        }

        def k8sService = new K8sServiceImpl(k8sClient: k8sClient)

        when:
        k8sService.deletePodWhenReachStatus(podName, statusName, timeout)

        then:
        0 * api.deleteNamespacedPod('test-pod', null, null, null, null, null, null, null)
    }

    def "getLatestPodForJob should return the latest pod when multiple pods are present"() {
        given:
        def jobName = "test-job"
        def pod1 = new V1Pod().metadata(new V1ObjectMeta().creationTimestamp(OffsetDateTime.now().minusDays(1)))
        def pod2 = new V1Pod().metadata(new V1ObjectMeta().creationTimestamp(OffsetDateTime.now()))
        def allPods = new V1PodList().items(Arrays.asList(pod1, pod2))
        def api = Mock(CoreV1Api)
        api.listNamespacedPod(_, _, _, _, _, "job-name=${jobName}", _, _, _, _, _, _) >> allPods
        def k8sClient = new K8sClient() {
            @Override
            ApiClient apiClient() {
                return null
            }
            CoreV1Api coreV1Api() {
                return api
            }
        }
        and:
        def k8sService = new K8sServiceImpl(k8sClient: k8sClient)

        when:
        def latestPod = k8sService.getLatestPodForJob(jobName)

        then:
        latestPod == pod2
    }

    def "getLatestPodForJob should return null when no pod is present"() {
        given:
        def jobName = "test-job"
        def api = Mock(CoreV1Api)
        api.listNamespacedPod(_, _, _, _, _, "job-name=${jobName}", _, _, _, _, _, _) >> null
        def k8sClient = new K8sClient() {
            @Override
            ApiClient apiClient() {
                return null
            }
            CoreV1Api coreV1Api() {
                return api
            }
        }
        and:
        def k8sService = new K8sServiceImpl(k8sClient: k8sClient)

        when:
        def latestPod = k8sService.getLatestPodForJob(jobName)

        then:
        latestPod == null
    }

    def 'buildJobSpec should create job with singularity image'() {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.service-account': 'theAdminAccount',
                'wave.build.deleteAfterFinished': '1d',
                'wave.build.retryAttempts': 3
        ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def name = 'test-job'
        def containerImage = 'singularity://test-image'
        def args = ['arg1', 'arg2']
        def workDir = Path.of('/work/dir')
        def credsFile = Path.of('/creds/file')
        def timeout = Duration.ofMinutes(10)
        def spackConfig = new SpackConfig(secretKeyFile: Path.of('/build/secret/key'), secretMountPath: '/secret/mount')
        def nodeSelector = [key: 'value']

        when:
        def job = k8sService.buildJobSpec(name, containerImage, args, workDir, credsFile, timeout, spackConfig, nodeSelector)

        then:
        job.spec.backoffLimit == 3
        job.spec.ttlSecondsAfterFinished == Duration.ofDays(1).seconds as Integer
        job.spec.template.spec.containers[0].image == containerImage
        job.spec.template.spec.containers[0].command == args
        job.spec.template.spec.containers[0].securityContext.privileged

        cleanup:
        ctx.close()
    }

    def 'buildJobSpec should create job with docker image'() {
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
        def name = 'test-job'
        def containerImage = 'docker://test-image'
        def args = ['arg1', 'arg2']
        def workDir = Path.of('/work/dir')
        def credsFile = Path.of('/creds/file')
        def timeout = Duration.ofMinutes(10)
        def spackConfig = new SpackConfig(secretKeyFile: Path.of('/build/secret/key'), secretMountPath: '/secret/mount')
        def nodeSelector = [key: 'value']

        when:
        def job = k8sService.buildJobSpec(name, containerImage, args, workDir, credsFile, timeout, spackConfig, nodeSelector)

        then:
        job.spec.template.spec.containers[0].image == containerImage
        job.spec.template.spec.containers[0].env.find { it.name == 'BUILDKITD_FLAGS' }
        job.spec.template.spec.containers[0].command == ['buildctl-daemonless.sh']
        job.spec.template.spec.containers[0].args == args

        cleanup:
        ctx.close()
    }

    def 'should create scan job spec with valid inputs'() {
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
        def name = 'scan-job'
        def containerImage = 'scan-image:latest'
        def args = ['arg1', 'arg2']
        def workDir = Path.of('/work/dir')
        def credsFile = Path.of('/creds/file')
        def scanConfig = Mock(ScanConfig) {
            getCacheDirectory() >> Path.of('/build/cache/dir')
            getRequestsCpu() >> '2'
            getRequestsMemory() >> '4Gi'
        }
        def nodeSelector = [key: 'value']

        when:
        def job = k8sService.scanJobSpec(name, containerImage, args, workDir, credsFile, scanConfig, nodeSelector)

        then:
        job.metadata.name == name
        job.metadata.namespace == 'foo'
        job.spec.template.spec.containers[0].image == containerImage
        job.spec.template.spec.containers[0].args == args
        job.spec.template.spec.containers[0].resources.requests.get('cpu') == new Quantity('2')
        job.spec.template.spec.containers[0].resources.requests.get('memory') == new Quantity('4Gi')
        job.spec.template.spec.volumes.size() == 1
        job.spec.template.spec.volumes[0].persistentVolumeClaim.claimName == 'bar'
        job.spec.template.spec.nodeSelector == nodeSelector
        job.spec.template.spec.restartPolicy == 'Never'

        cleanup:
        ctx.close()
    }

    def 'should create scan job spec without creds file'() {
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
        def name = 'scan-job'
        def containerImage = 'scan-image:latest'
        def args = ['arg1', 'arg2']
        def workDir = Path.of('/work/dir')
        def credsFile = null
        def scanConfig = Mock(ScanConfig) {
            getCacheDirectory() >> Path.of('/build/cache/dir')
            getRequestsCpu() >> '2'
            getRequestsMemory() >> '4Gi'
        }
        def nodeSelector = [key: 'value']

        when:
        def job = k8sService.scanJobSpec(name, containerImage, args, workDir, credsFile, scanConfig, nodeSelector)

        then:
        job.metadata.name == name
        job.metadata.namespace == 'foo'
        job.spec.template.spec.containers[0].image == containerImage
        job.spec.template.spec.containers[0].args == args
        job.spec.template.spec.containers[0].resources.requests.get('cpu') == new Quantity('2')
        job.spec.template.spec.containers[0].resources.requests.get('memory') == new Quantity('4Gi')
        job.spec.template.spec.volumes.size() == 1
        job.spec.template.spec.volumes[0].persistentVolumeClaim.claimName == 'bar'
        job.spec.template.spec.nodeSelector == nodeSelector
        job.spec.template.spec.restartPolicy == 'Never'

        cleanup:
        ctx.close()
    }

    def 'should create scan job spec without node selector'() {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.service-account': 'theAdminAccount',
                'wave.build.deleteAfterFinished': '1d',
        ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def name = 'scan-job'
        def containerImage = 'scan-image:latest'
        def args = ['arg1', 'arg2']
        def workDir = Path.of('/work/dir')
        def credsFile = Path.of('/creds/file')
        def scanConfig = Mock(ScanConfig) {
            getCacheDirectory() >> Path.of('/build/cache/dir')
            getRequestsCpu() >> '2'
            getRequestsMemory() >> '4Gi'
            getRetryAttempts() >> 3
        }
        def nodeSelector = null

        when:
        def job = k8sService.scanJobSpec(name, containerImage, args, workDir, credsFile, scanConfig, nodeSelector)

        then:
        job.metadata.name == name
        job.metadata.namespace == 'foo'
        job.spec.backoffLimit == 3
        job.spec.ttlSecondsAfterFinished == Duration.ofDays(1).seconds as Integer
        job.spec.template.spec.containers[0].image == containerImage
        job.spec.template.spec.containers[0].args == args
        job.spec.template.spec.containers[0].resources.requests.get('cpu') == new Quantity('2')
        job.spec.template.spec.containers[0].resources.requests.get('memory') == new Quantity('4Gi')
        job.spec.template.spec.volumes.size() == 1
        job.spec.template.spec.volumes[0].persistentVolumeClaim.claimName == 'bar'
        job.spec.template.spec.nodeSelector == null
        job.spec.template.spec.restartPolicy == 'Never'

        cleanup:
        ctx.close()
    }

    def 'should create scan job spec without resource requests'() {
        given:
        def PROPS = [
                'wave.build.workspace': '/build/work',
                'wave.build.k8s.namespace': 'foo',
                'wave.build.k8s.configPath': '/home/kube.config',
                'wave.build.k8s.storage.claimName': 'bar',
                'wave.build.k8s.storage.mountPath': '/build',
                'wave.build.k8s.service-account': 'theAdminAccount',
                'wave.build.deleteAfterFinished': '1d',
                'wave.scan.retryAttempts': 3
        ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)
        def name = 'scan-job'
        def containerImage = 'scan-image:latest'
        def args = ['arg1', 'arg2']
        def workDir = Path.of('/work/dir')
        def credsFile = Path.of('/creds/file')
        def scanConfig = Mock(ScanConfig) {
            getCacheDirectory() >> Path.of('/build/cache/dir')
            getRequestsCpu() >> null
            getRequestsMemory() >> null
            getRetryAttempts() >> 3
        }
        def nodeSelector = [key: 'value']

        when:
        def job = k8sService.scanJobSpec(name, containerImage, args, workDir, credsFile, scanConfig, nodeSelector)

        then:
        job.metadata.name == name
        job.metadata.namespace == 'foo'
        job.spec.backoffLimit == 3
        job.spec.ttlSecondsAfterFinished == Duration.ofDays(1).seconds as Integer
        job.spec.template.spec.containers[0].image == containerImage
        job.spec.template.spec.containers[0].args == args
        job.spec.template.spec.containers[0].resources.requests == null
        job.spec.template.spec.volumes.size() == 1
        job.spec.template.spec.volumes[0].persistentVolumeClaim.claimName == 'bar'
        job.spec.template.spec.nodeSelector == nodeSelector
        job.spec.template.spec.restartPolicy == 'Never'

        cleanup:
        ctx.close()
    }

}
