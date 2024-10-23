/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class DockerBuildStrategyTest extends Specification {

    def 'should get docker command' () {
        given:
        def ctx = ApplicationContext.run()
        and:
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def work = Path.of('/work/foo')
        when:
        def cmd = service.cmdForBuildkit('build-job-name', work, null, null)
        def name = 'build-job-name'
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                name,
                '--privileged',
                '-v', '/work/foo:/work/foo',
                '--entrypoint',
                'buildctl-daemonless.sh',
                'moby/buildkit:v0.14.1-rootless']

        when:
        cmd = service.cmdForBuildkit(name, work, Path.of('/foo/creds.json'), ContainerPlatform.of('arm64'))
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-v', '/work/foo:/work/foo',
                '--entrypoint',
                'buildctl-daemonless.sh',
                '-v', '/foo/creds.json:/home/user/.docker/config.json:ro',
                '--platform', 'linux/arm64',
                'moby/buildkit:v0.14.1-rootless']

        when:
        cmd = service.cmdForBuildkit('build-job-name', work, Path.of('/foo/creds.json'), null)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-v', '/work/foo:/work/foo',
                '--entrypoint',
                'buildctl-daemonless.sh',
                '-v', '/foo/creds.json:/home/user/.docker/config.json:ro',
                'moby/buildkit:v0.14.1-rootless']

        cleanup:
        ctx.close()
    }

    def 'should get buildkit build command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def creds = Path.of('/work/creds.json')
        and:
        def req = new BuildRequest(
                id: '89fb83ce6ec8627b',
                containerId: '89fb83ce6ec8627b',
                buildId: 'bd-89fb83ce6ec8627b_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'repo:89fb83ce6ec8627b',
                cacheRepository: 'reg.io/wave/build/cache' )
        when:
        def cmd = service.buildCmd('build-job-name', req, creds)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-v', '/work/foo/bd-89fb83ce6ec8627b_1:/work/foo/bd-89fb83ce6ec8627b_1',
                '--entrypoint',
                'buildctl-daemonless.sh',
                '-v', '/work/creds.json:/home/user/.docker/config.json:ro',
                '--platform', 'linux/amd64',
                'moby/buildkit:v0.14.1-rootless',
                'build',
                '--frontend',
                'dockerfile.v0',
                '--local',
                'dockerfile=/work/foo/bd-89fb83ce6ec8627b_1',
                '--opt',
                'filename=Containerfile',
                '--local',
                'context=/work/foo/bd-89fb83ce6ec8627b_1/context',
                '--output',
                'type=image,name=repo:89fb83ce6ec8627b,push=true,oci-mediatypes=true',
                '--opt',
                'platform=linux/amd64',
                '--export-cache',
                'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:89fb83ce6ec8627b,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=false',
                '--import-cache',
                'type=registry,ref=reg.io/wave/build/cache:89fb83ce6ec8627b' ]

        cleanup:
        ctx.close()
    }

    def 'should get singularity build command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def creds = Path.of('/work/creds.json')
        and:
        def req = new BuildRequest(
                containerId: 'd4869cc39b8d7d55',
                buildId: 'bd-d4869cc39b8d7d55_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'oras://repo:d4869cc39b8d7d55',
                cacheRepository: 'reg.io/wave/build/cache',
                format: BuildFormat.SINGULARITY  )
        when:
        def cmd = service.buildCmd('build-job-name', req, creds)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '--entrypoint', '',
                '-v', '/work/foo/bd-d4869cc39b8d7d55_1:/work/foo/bd-d4869cc39b8d7d55_1',
                '-v', '/work/creds.json:/root/.singularity/docker-config.json:ro',
                '-v', '/work/singularity-remote.yaml:/root/.singularity/remote.yaml:ro',
                '--platform', 'linux/amd64',
                'quay.io/singularity/singularity:v3.11.4-slim',
                'sh',
                '-c',
                'singularity build image.sif /work/foo/bd-d4869cc39b8d7d55_1/Containerfile && singularity push image.sif oras://repo:d4869cc39b8d7d55'
        ]

        cleanup:
        ctx.close()
    }

    def 'should get singularity build command for arm64 architecture' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def creds = Path.of('/work/creds.json')
        and:
        def req = new BuildRequest(
                containerId: '9c68af894bb2419c',
                buildId: 'bd-9c68af894bb2419c_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/arm64'),
                targetImage: 'oras://repo:9c68af894bb2419c',
                cacheRepository: 'reg.io/wave/build/cache',
                format: BuildFormat.SINGULARITY )
        when:
        def cmd = service.buildCmd('build-job-name', req, creds)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '--entrypoint', '',
                '-v', '/work/foo/bd-9c68af894bb2419c_1:/work/foo/bd-9c68af894bb2419c_1',
                '-v', '/work/creds.json:/root/.singularity/docker-config.json:ro',
                '-v', '/work/singularity-remote.yaml:/root/.singularity/remote.yaml:ro',
                '--platform', 'linux/arm64',
                'quay.io/singularity/singularity:v3.11.4-slim-arm64',
                'sh',
                '-c',
                'singularity build image.sif /work/foo/bd-9c68af894bb2419c_1/Containerfile && singularity push image.sif oras://repo:9c68af894bb2419c'
        ]

        cleanup:
        ctx.close()
    }
}
