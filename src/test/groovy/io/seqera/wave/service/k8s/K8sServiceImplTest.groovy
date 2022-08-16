package io.seqera.wave.service.k8s

import spock.lang.Specification

import java.nio.file.Path

import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource
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

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/'), '/foo')
        then:
        result.name == 'build-data'
        result.mountPath == '/foo'
        result.subPath == null

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/work/x1'), '/foo')
        then:
        result.name == 'build-data'
        result.mountPath == '/foo'
        result.subPath == 'work/x1'

        when:
        result = k8sService.mountBuildStorage(Path.of('/foo/work/x1'), null)
        then:
        result.name == 'build-data'
        result.mountPath == '/foo/work/x1'

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
        def result = k8sService.volumeBuildStorage(Path.of('/foo/work/x1'), null)
        then:
        result.name == 'build-data'
        result.hostPath.path == '/foo/work/x1'

        when:
        result = k8sService.volumeBuildStorage(Path.of('/foo/work/x1'), 'foo')
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
        def vol = k8sService.volumeDockerConfig()
        then:
        vol.name == 'docker-config'
        vol.emptyDir instanceof V1EmptyDirVolumeSource

        when:
        def mount = k8sService.mountDockerConfig()
        then:
        mount.name == 'docker-config'
        mount.mountPath == '/kaniko/.docker/'

        cleanup:
        ctx.close()
    }
}
