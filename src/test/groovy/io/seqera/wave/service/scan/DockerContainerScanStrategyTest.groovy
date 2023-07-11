package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import io.micronaut.context.ApplicationContext

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class DockerContainerScanStrategyTest extends Specification {

    def 'should get docker command' () {
        given:
        def workspace = Files.createTempDirectory('test')
        def props = ['wave.build.workspace': workspace.toString()]
        and:
        def ctx = ApplicationContext.run(props)
        and:
        def dockerContainerStrategy = ctx.getBean(DockerContainerScanStrategy)

        when:
        def command = dockerContainerStrategy.dockerWrapper(Path.of("/user/test/build-workspace/config.json"))

        then:
        command == [
                'docker',
                'run',
                '--rm',
                '-v',
                '/user/test/build-workspace/config.json:/root/.docker/config.json:ro',
                '-v',
                 "$workspace/.trivy-cache:/root/.cache/:rw" ]

        cleanup:
        ctx.close()
        workspace?.deleteDir()
    }
}
