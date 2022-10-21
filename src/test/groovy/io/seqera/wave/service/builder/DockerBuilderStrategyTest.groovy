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
        def cmd = service.dockerWrapper(work, null)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                'gcr.io/kaniko-project/executor:v1.9.0']

        when:
        cmd = service.dockerWrapper(work, Path.of('/foo/creds.json'))
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo',
                '-v', '/work/foo:/work/foo',
                '-v', '/foo/creds.json:/kaniko/.docker/config.json:ro',
                'gcr.io/kaniko-project/executor:v1.9.0']

        cleanup:
        ctx.close()
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
        cmd == ['docker',
                'run',
                '--rm',
                '-w', '/work/foo/2f6d45fae801bd000d175c58148354cc',
                '-v', '/work/foo/2f6d45fae801bd000d175c58148354cc:/work/foo/2f6d45fae801bd000d175c58148354cc',
                '-v', '/work/creds.json:/kaniko/.docker/config.json:ro',
                'gcr.io/kaniko-project/executor:v1.9.0',
                '--dockerfile', '/work/foo/2f6d45fae801bd000d175c58148354cc/Dockerfile',
                '--context', '/work/foo/2f6d45fae801bd000d175c58148354cc',
                '--destination', 'repo:2f6d45fae801bd000d175c58148354cc',
                '--cache=true',
                '--cache-repo', 'reg.io/wave/build/cache'
        ]

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
        cmd == [
                '--dockerfile', '/work/foo/2f6d45fae801bd000d175c58148354cc/Dockerfile',
                '--context', '/work/foo/2f6d45fae801bd000d175c58148354cc',
                '--destination', 'repo:2f6d45fae801bd000d175c58148354cc',
                '--cache=true',
                '--cache-repo', 'reg.io/wave/build/cache',
                '--compressed-caching', 'false'
        ]

        cleanup:
        ctx.close()
    }
}
