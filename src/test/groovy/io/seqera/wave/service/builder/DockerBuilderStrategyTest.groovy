package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class DockerBuilderStrategyTest extends Specification {

    def 'should get docker command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        when:
        def cmd = service.dockerWrapper(work, null, null)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                'gcr.io/kaniko-project/executor:v1.9.1']

        when:
        cmd = service.dockerWrapper(work, Path.of('/foo/creds.json'), ContainerPlatform.of('arm64'))
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                '-v', '/foo/creds.json:/kaniko/.docker/config.json:ro',
                '--platform', 'linux/arm64/v8',
                'gcr.io/kaniko-project/executor:v1.9.1']
    }

    def 'should get build command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def creds = Path.of('/work/creds.json')
        def cache = 'reg.io/wave/build/cache'
        def req = new BuildRequest('from foo', work, 'repo', null, Mock(User), ContainerPlatform.of('amd64'),'{auth}', cache, "1.2.3.4")
        when:
        def cmd = service.buildCmd(req, creds)
        then:
        cmd.sort() == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f',
                '-v', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f:/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f',
                '-v', '/work/creds.json:/kaniko/.docker/config.json:ro',
                '--platform', 'linux/amd64',
                'gcr.io/kaniko-project/executor:v1.9.1',
                '--dockerfile', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/Dockerfile',
                '--context', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f',
                '--destination', 'repo:17e58f4434c26104c2cf9f0eb8fbc16f',
                '--cache=true',
                '--custom-platform', 'linux/amd64',
                '--cache-repo', 'reg.io/wave/build/cache'
        ].sort()

        cleanup:
        ctx.close()
    }

    def 'should disable compress-caching' () {
        given:
        def ctx = ApplicationContext.run(['wave.build.compress-caching': false])
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def creds = Path.of('/work/creds.json')
        def cache = 'reg.io/wave/build/cache'
        def req = new BuildRequest('from foo', work, 'repo', null, Mock(User), ContainerPlatform.of('amd64'),'{auth}', cache, "1.2.3.4")
        when:
        def cmd = service.launchCmd(req)
        then:
        cmd.sort() == [
                '--dockerfile', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/Dockerfile',
                '--context', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f',
                '--destination', 'repo:17e58f4434c26104c2cf9f0eb8fbc16f',
                '--cache=true',
                '--custom-platform', 'linux/amd64',
                '--cache-repo', 'reg.io/wave/build/cache',
                '--compressed-caching', 'false'
        ].sort()

        cleanup:
        ctx.close()
    }
}
