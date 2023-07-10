package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Files

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.ContainerScanConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.k8s.K8sServiceImpl
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name="wave.build.k8s.namespace",value="foo")
@Property(name="wave.build.k8s.configPath",value="/home/kube.config")
@Property(name="wave.build.k8s.storage.claimName",value="bar")
@Property(name="wave.build.k8s.storage.mountPath",value="/build")
@Property(name='wave.scan.k8s.node-selector[linux/amd64]',value="service=wave-scan")
@Property(name='wave.scan.k8s.node-selector[linux/arm64]',value="service=wave-scan-arm64")
class KubernetesContainerScanStrategyTest extends Specification {

    @Inject
    KubernetesContainerScanStrategy strategy

    @Inject
    ContainerScanConfig containerScanConfig

    @Inject
    K8sService k8sService

    @MockBean(K8sServiceImpl)
    K8sService k8sService(){
        Mock(K8sService)
    }

    def 'should get platform selector' () {
        expect:
        strategy.getSelectorLabel(ContainerPlatform.of(PLATFORM), SELECTORS) == EXPECTED

        where:
        PLATFORM        | SELECTORS                                             | EXPECTED
        'amd64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['foo': '1']
        'arm64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['bar': '2']
        and:
        'amd64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'x86_64'        | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'arm64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['bar': '2']

    }

    def 'should check unmatched platform' () {
        expect:
        strategy.getSelectorLabel(ContainerPlatform.of('amd64'), [:]) == [:]

        when:
        strategy.getSelectorLabel(ContainerPlatform.of('amd64'), [arm64:'x=1'])
        then:
        def err = thrown(BadRequestException)
        err.message == "Unsupported container platform 'linux/amd64'"
    }

    def "request to scan a container with right selector"(){
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Files.createTempDirectory('test')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'

        when:
        def buildRequest = new BuildRequest('from foo', PATH, repo, null, null, USER, ContainerPlatform.of('amd64'),'{}', cache, "")
        Files.createDirectories(buildRequest.workDir)

        def scanResult = strategy.scanContainer(containerScanConfig.scannerImage, buildRequest)
        then:
        scanResult
        and:
        1 * k8sService.scanContainer(_, _, _, _, _, [service:'wave-scan'], _) >> null

        when:
        def buildRequest2 = new BuildRequest('from foo', PATH, repo, null, null, USER, ContainerPlatform.of('arm64'),'{}', cache, "")
        Files.createDirectories(buildRequest.workDir)

        def scanResult2 = strategy.scanContainer(containerScanConfig.scannerImage, buildRequest2)
        then:
        scanResult2
        and:
        1 * k8sService.scanContainer(_, _, _, _, _, [service:'wave-scan-arm64'], _) >> null
    }
}
