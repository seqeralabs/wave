package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.SpackConfig
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
        def props = [
                'wave.build.spack.cacheDirectory':'/host/spack/cache',
                'wave.build.spack.cacheMountPath':'/opt/spack/cache',
                'wave.build.spack.secretKeyFile':'/host/spack/key',
                'wave.build.spack.secretMountPath':'/opt/spack/key'  ]
        def ctx = ApplicationContext.run(props)
        and:
        def service = ctx.getBean(DockerBuildStrategy)
        def spackConfig = ctx.getBean(SpackConfig)
        and:
        def work = Path.of('/work/foo')
        when:
        def cmd = service.cmdForKaniko(work, null, null, null)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-v', '/work/foo:/work/foo',
                'gcr.io/kaniko-project/executor:v1.12.1']

        when:
        cmd = service.cmdForKaniko(work, Path.of('/foo/creds.json'), null, ContainerPlatform.of('arm64'))
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-v', '/work/foo:/work/foo',
                '-v', '/foo/creds.json:/kaniko/.docker/config.json:ro',
                '--platform', 'linux/arm64',
                'gcr.io/kaniko-project/executor:v1.12.1']

        when:
        cmd = service.cmdForKaniko(work, Path.of('/foo/creds.json'), spackConfig, null)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-v', '/work/foo:/work/foo',
                '-v', '/foo/creds.json:/kaniko/.docker/config.json:ro',
                '-v', '/host/spack/key:/opt/spack/key:ro',
                '-v', '/host/spack/cache:/opt/spack/cache:rw',
                'gcr.io/kaniko-project/executor:v1.12.1']


        cleanup:
        ctx.close()
    }

    def 'should get kaniko build command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def creds = Path.of('/work/creds.json')
        def cache = 'reg.io/wave/build/cache'
        def req = new BuildRequest('from foo', work, 'repo', null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "1.2.3.4", null)
        when:
        def cmd = service.buildCmd(req, creds)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '-v', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f:/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f',
                '-v', '/work/creds.json:/kaniko/.docker/config.json:ro',
                '--platform', 'linux/amd64',
                'gcr.io/kaniko-project/executor:v1.12.1',
                '--dockerfile', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/Containerfile',
                '--context', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/context',
                '--destination', 'repo:17e58f4434c26104c2cf9f0eb8fbc16f',
                '--cache=true',
                '--custom-platform', 'linux/amd64',
                '--cache-repo', 'reg.io/wave/build/cache' ]

        cleanup:
        ctx.close()
    }

    def 'should disable compress-caching' () {
        given:
        def ctx = ApplicationContext.run(['wave.build.compress-caching': false])
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def cache    = 'reg.io/wave/build/cache'
        def req = new BuildRequest('from foo', work, 'repo', null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "1.2.3.4", null)
        when:
        def cmd = service.launchCmd(req)
        then:
        cmd == [
                '--dockerfile', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/Containerfile',
                '--context', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/context',
                '--destination', 'repo:17e58f4434c26104c2cf9f0eb8fbc16f',
                '--cache=true',
                '--custom-platform', 'linux/amd64',
                '--cache-repo', 'reg.io/wave/build/cache',
                '--compressed-caching=false' ]

        cleanup:
        ctx.close()
    }

    def 'should get singularity build command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def creds = Path.of('/work/creds.json')
        def req = new BuildRequest('from foo', work, 'repo', null, null, BuildFormat.SINGULARITY, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', null, null, "1.2.3.4", null)
        when:
        def cmd = service.buildCmd(req, creds)
        then:
        cmd == ['docker',
                'run',
                '--rm',
                '--privileged',
                '--entrypoint', '',
                '-v', '/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f:/work/foo/17e58f4434c26104c2cf9f0eb8fbc16f',
                '-v', '/work/creds.json:/root/.singularity/docker-config.json:ro',
                '-v', '/work/singularity-remote.yaml:/root/.singularity/remote.yaml:ro',
                '--platform', 'linux/amd64',
                'quay.io/singularity/singularity:v3.11.4-slim',
                'sh',
                '-c',
                'singularity build image.sif /work/foo/17e58f4434c26104c2cf9f0eb8fbc16f/Containerfile --platform linux/amd64 && singularity push image.sif oras://repo:17e58f4434c26104c2cf9f0eb8fbc16f'
        ]
        
        cleanup:
        ctx.close()
    }
}
