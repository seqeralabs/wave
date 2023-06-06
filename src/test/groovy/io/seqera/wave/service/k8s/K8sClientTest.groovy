package io.seqera.wave.service.k8s

import spock.lang.Ignore
import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Ignore
@MicronautTest
class K8sClientTest extends Specification {

    @Inject K8sService k8sService

    def 'should create job' () {
        when:
        def job = k8sService.createJob('foo-2', 'busybox', ['sh', '-c', 'slep 10'])
        println job
        then:
        job
    }

    def 'should get job' () {
        when:
        def job = k8sService.getJob('foo-2')
        job.status.succeeded == 1
        then:
        true
    }

    def 'should create pod' () {
        when:
        def pod = k8sService.buildContainer(
                'my-pod',
                'busybox',
                ['cat','/kaniko/.docker/config.json'],
                Path.of('/work/dir'),
                Path.of('/creds'),
                Path.of('/spack/dir'),
                ['my-creds': 'selector'])
        then:
        true

        when:
        def str = k8sService.logsPod('my-pod')
        then:
        str

    }
}
