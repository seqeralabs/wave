package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.context.ApplicationContext
import io.seqera.wave.configuration.ContainerScanConfig
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class DockerContainerScanStrategyTest extends Specification {
    def 'should get docker command' () {
        given:
        def props =[
                'wave.scan.image.name':'scanimage',
                'wave.scan.cacheDirectory':'/host/scan/cache',
                'wave.scan.workspace':'scan-test-workspace']
        def ctx = ApplicationContext.run(props)
        and:
        def dockerContainerStrategy = ctx.getBean(DockerContainerScanStrategy)
        def containerScanConfig = ctx.getBean(ContainerScanConfig)
        when:
        def command = dockerContainerStrategy.dockerWrapper(Path.of("/user/test/${containerScanConfig.workspace}/config.json"))
        then:
        command == [
                'docker',
                'run',
                '--rm',
                '-v',
                '/user/test/scan-test-workspace/config.json:/root/.docker/config.json:ro',
                '-v',
                '/host/scan/cache:/root/.cache/:rw']
    }
}
