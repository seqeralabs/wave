package io.seqera.wave.service.builder


import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.time.Duration
import java.time.Instant

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.ia>
 */
@MicronautTest
class FutureContainerBuildServiceTest extends Specification {

    @Inject
    ContainerBuildServiceImpl service

    int exitCode

    @MockBean(BuildStrategy)
    BuildStrategy fakeBuildStrategy(){
        new BuildStrategy() {
            @Override
            BuildResult build(BuildRequest req, String creds) {
                new BuildResult("", exitCode, "a fake build result in a test", Instant.now(), Duration.ofSeconds(3))
            }
        }
    }


    @Timeout(5)
    def 'should wait to build container completion' () {
        given:
        def folder = Files.createTempDirectory('test')
        def repo = '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build'
        and:
        def dockerfile = """
        FROM busybox
        RUN echo $EXIT_CODE > hello.txt
        """.stripIndent()
        and:
        def REQ = new BuildRequest(dockerfile, folder, repo, null, Mock(User), ContainerPlatform.of('amd64'))

        when:
        exitCode = EXIT_CODE
        def result = service.getOrSubmit(REQ)
        then:
        result

        when:
        def status = service.buildResult(result).get()
        then:
        status.getExitStatus() == EXIT_CODE

        cleanup:
        folder?.deleteDir()

        where:
        EXIT_CODE | _
        0         | _
        1         | _
    }

}
