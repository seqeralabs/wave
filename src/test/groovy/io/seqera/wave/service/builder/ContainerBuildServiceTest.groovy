package io.seqera.wave.service.builder

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files

import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerBuildServiceTest extends Specification {

    @Inject
    ContainerBuildServiceImpl service

    @Value('${wave.registries.docker.username}')
    String dockerUser

    @Value('${wave.registries.docker.password}')
    String dockerPass

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should build & push container' () {
        given:
        def folder = Files.createTempDirectory('test')
        def repo = '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build'
        and:
        def dockerfile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def REQ = new BuildRequest(dockerfile, folder, repo, null, Mock(User), 'amd64')

        when:
        def result = service.launch(REQ)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    def 'should create creds json file' () {
        given:
        def EXPECTED = "$dockerUser:$dockerPass".bytes.encodeBase64()
        when:
        def result = service.credsJson('docker.io')
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://registry-1.docker.io":{"auth":"$EXPECTED"}}}"""
    }
}
