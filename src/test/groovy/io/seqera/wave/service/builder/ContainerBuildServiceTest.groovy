package io.seqera.wave.service.builder

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

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

    def 'should get docker command' () {
        given:
        def work = Path.of('/work/foo')
        when:
        def cmd = service.dockerWrapper(work)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                '-e', 'AWS_ACCESS_KEY_ID',
                '-e', 'AWS_SECRET_ACCESS_KEY',
                '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/kaniko:0.1.2']
    }

    def 'should get kaniko command' () {
        given:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, Mock(User))

        when:
        def cmd = service.launchCmd(REQ, true)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d',
                '-v', '/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d:/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d',
                '-e', 'AWS_ACCESS_KEY_ID',
                '-e', 'AWS_SECRET_ACCESS_KEY',
                '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/kaniko:0.1.2',
                '/kaniko/executor',
                '--dockerfile',
                '/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d/Dockerfile',
                '--context',
                '/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d',
                '--destination',
                'quay.io/wave:7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d',
                '--cache=true',
                '--cache-repo',
                '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build/cache'
        ]

        when:
        cmd = service.launchCmd(REQ, false)
        then:
        cmd == [
                '/kaniko/executor',
                '--dockerfile',
                '/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d/Dockerfile',
                '--context',
                '/work/foo/7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d',
                '--destination',
                'quay.io/wave:7a3e00f5a5d41298cbb8b61ac280b7418f198677df8b330cae1602546d34c24d',
                '--cache=true',
                '--cache-repo',
                '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build/cache'
        ]
    }

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
        def REQ = new BuildRequest(dockerfile, folder, repo, null, Mock(User))

        when:
        def result = service.launch(REQ, true)
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }
}
