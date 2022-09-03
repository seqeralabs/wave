package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class DockerBuilderStrategyTest extends Specification {

    @Inject
    DockerBuildStrategy service

    def 'should get docker command' () {
        given:
        def work = Path.of('/work/foo')
        when:
        def cmd = service.dockerWrapper(work, null)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                'gcr.io/kaniko-project/executor:latest']

        when:
        cmd = service.dockerWrapper(work, Path.of('/foo/creds.json'))
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                '-v', '/foo/creds.json:/kaniko/.docker/config.json:ro',
                'gcr.io/kaniko-project/executor:latest']
    }

    def 'should get build command' () {
        given:
        def work = Path.of('/work/foo')
        def creds = Path.of('/work/creds.json')
        def req = new BuildRequest('from foo', work, 'repo', null, Mock(User), ContainerPlatform.of('amd64'))
        when:
        def cmd = service.buildCmd(req, creds)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo/371f47bac77d67d55d29e0c111c508ef',
                '-v', '/work/foo/371f47bac77d67d55d29e0c111c508ef:/work/foo/371f47bac77d67d55d29e0c111c508ef',
                '-v', '/work/creds.json:/kaniko/.docker/config.json:ro',
                'gcr.io/kaniko-project/executor:latest',
                '--dockerfile', '/work/foo/371f47bac77d67d55d29e0c111c508ef/Dockerfile',
                '--context', '/work/foo/371f47bac77d67d55d29e0c111c508ef',
                '--destination', 'repo:371f47bac77d67d55d29e0c111c508ef',
                '--cache=true',
                '--cache-repo', '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build/cache'
        ]

    }

}
