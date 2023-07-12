package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import io.micronaut.context.ApplicationContext
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class DockerScanStrategyTest extends Specification {

    def 'should get docker command' () {
        given:
        def workspace = Files.createTempDirectory('test')
        def props = ['wave.build.workspace': workspace.toString()]
        and:
        def ctx = ApplicationContext.run(props)
        and:
        def dockerContainerStrategy = ctx.getBean(DockerScanStrategy)

        when:
        def scanDir = Path.of('/some/scan/dir')
        def config = Path.of("/user/test/build-workspace/config.json")
        def command = dockerContainerStrategy.dockerWrapper(scanDir, config)

        then:
        command == [
                'docker',
                'run',
                '--rm',
                '-w',
                '/some/scan/dir',
                '-v',
                '/some/scan/dir:/some/scan/dir:rw',
                '-v',
                 "$workspace/.trivy-cache:/root/.cache/:rw",
                '-v',
                '/user/test/build-workspace/config.json:/root/.docker/config.json:ro'
        ]

        cleanup:
        ctx.close()
        workspace?.deleteDir()
    }
}
