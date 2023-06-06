package io.seqera.wave.service.k8s

import spock.lang.Specification

import java.nio.file.Path

import io.kubernetes.client.custom.Quantity
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
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
        def result = k8sService.mountBuildStorage(Path.of('/foo'), '/foo')
        then:
        result.name == 'build-data'
        result.mountPath == '/foo'
        result.subPath == null
        result.readOnly

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/'), '/foo')
        then:
        result.name == 'build-data'
        result.mountPath == '/foo'
        result.subPath == null
        result.readOnly

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/work/x1'), '/foo')
        then:
        result.name == 'build-data'
        result.mountPath == '/foo/work/x1'
        result.subPath == 'work/x1'
        result.readOnly

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/work/x1'), null)
        then:
        result.name == 'build-data'
        result.mountPath == '/foo/work/x1'
        result.readOnly

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
        def mount = k8sService.mountDockerConfig(Path.of('/foo/work/x1'), '/foo')
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

    def 'should create build pod' () {
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), Path.of('secret'), null, [:])
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
        result.spec.containers.get(0).volumeMounts.get(0).mountPath == '/kaniko/.docker/config.json'
        result.spec.containers.get(0).volumeMounts.get(0).subPath == 'work/xyz/config.json'
        and:
        result.spec.containers.get(0).volumeMounts.get(1).name == 'build-data'
        result.spec.containers.get(0).volumeMounts.get(1).mountPath == '/build/work/xyz'
        result.spec.containers.get(0).volumeMounts.get(1).subPath == 'work/xyz'

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
                'wave.build.k8s.storage.mountPath': '/build' ]
        and:
        def ctx = ApplicationContext.run(PROPS)
        def k8sService = ctx.getBean(K8sServiceImpl)

        when:
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, Path.of('/build/host/cache'), [:])
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
        result.spec.containers.get(0).volumeMounts.get(1).mountPath == '/var/seqera/spack/cache'
        result.spec.containers.get(0).volumeMounts.get(1).subPath == 'host/cache'

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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null,[:])
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null,[:])
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null, PROPS['wave.build.k8s.node-selector'] as Map<String,String>)
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
        def result = k8sService.buildSpec('foo', 'my-image:latest', ['this','that'], Path.of('/build/work/xyz'), null, null,[:])
        then:
        result.spec.serviceAccount == PROPS['wave.build.k8s.service-account']
        and:
        ctx.close()
    }
}
