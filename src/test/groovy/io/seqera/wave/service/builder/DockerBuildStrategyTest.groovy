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

    final testEnv =[
            AWS_ACCESS_KEY_ID: 'test',
            AWS_SECRET_ACCESS_KEY: 'test',
    ]

    def 'should get docker command' () {
        given:
        def ctx = ApplicationContext.run()
        and:
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def req = new BuildRequest(
                containerId: '89fb83ce6ec8627b',
                buildId: 'bd-89fb83ce6ec8627b_1',
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'repo:89fb83ce6ec8627b',
                cacheRepository: 'reg.io/wave/build/cache' ,
                configJson: '{"config": "json"}' )
        when:
        def cmd = service.cmdForBuildkit('build-job-name', req, testEnv)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-e', 'TMPDIR=/tmp',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '-e', 'DOCKER_CONFIG=/fusion/s3/nextflow-ci/wave-build/workspace/bd-89fb83ce6ec8627b_1',
                '--platform', 'linux/amd64',
                'hrma017/buildkit:v0.22.0-2.4.13']

        when:
        req = new BuildRequest(
                containerId: '89fb83ce6ec8627b',
                buildId: 'bd-89fb83ce6ec8627b_1',
                platform: ContainerPlatform.of('linux/arm64'),
                targetImage: 'repo:89fb83ce6ec8627b',
                cacheRepository: 'reg.io/wave/build/cache',
                configJson: '{"config": "json"}' )
        and:
        cmd = service.cmdForBuildkit('build-job-name', req, testEnv)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-e', 'TMPDIR=/tmp',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '-e', 'DOCKER_CONFIG=/fusion/s3/nextflow-ci/wave-build/workspace/bd-89fb83ce6ec8627b_1',
                '--platform', 'linux/arm64',
                'hrma017/buildkit:v0.22.0-2.4.13']

        when:
        req = new BuildRequest(
                containerId: '89fb83ce6ec8627b',
                buildId: 'bd-89fb83ce6ec8627b_1',
                platform: ContainerPlatform.of('linux/arm64'),
                targetImage: 'repo:89fb83ce6ec8627b',
                cacheRepository: 'reg.io/wave/build/cache' )
        cmd = service.cmdForBuildkit('build-job-name', req, testEnv)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-e', 'TMPDIR=/tmp',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '--platform', 'linux/arm64',
                'hrma017/buildkit:v0.22.0-2.4.13']

        cleanup:
        ctx.close()
    }

    def 'should get buildkit build command' () {
        given:
        def ctx = ApplicationContext.run()
        def service = ctx.getBean(DockerBuildStrategy)
        and:
        def req = new BuildRequest(
                containerId: '89fb83ce6ec8627b',
                buildId: 'bd-89fb83ce6ec8627b_1',
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'repo:89fb83ce6ec8627b',
                cacheRepository: 'reg.io/wave/build/cache' )
        when:
        def cmd = service.buildCmd('build-job-name', req, testEnv)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-e', 'TMPDIR=/tmp',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '--platform', 'linux/amd64',
                'hrma017/buildkit:v0.22.0-2.4.13',
                'fusion',
                'buildctl-daemonless.sh',
                'build',
                '--frontend',
                'dockerfile.v0',
                '--local',
                'dockerfile=/fusion/s3/nextflow-ci/wave-build/workspace/bd-89fb83ce6ec8627b_1',
                '--opt',
                'filename=Containerfile',
                '--local',
                'context=/fusion/s3/nextflow-ci/wave-build/workspace/bd-89fb83ce6ec8627b_1/context',
                '--output',
                'type=image,name=repo:89fb83ce6ec8627b,push=true,oci-mediatypes=true',
                '--opt',
                'platform=linux/amd64',
                '--export-cache',
                'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:89fb83ce6ec8627b,mode=max,ignore-error=true,oci-mediatypes=true',
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
                format: BuildFormat.SINGULARITY ,
                configJson: '{"config": "json"}' )
        when:
        def cmd = service.buildCmd('build-job-name', req, testEnv)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '--platform', 'linux/amd64',
                'hrma017/singularity:v4.2.1-r5-2.4.13',
                'sh',
                '-c',
                'ln -s /fusion/s3/nextflow-ci/wave-build/workspace/bd-d4869cc39b8d7d55_1/.singularity /root/.singularity && singularity build image.sif /fusion/s3/nextflow-ci/wave-build/workspace/bd-d4869cc39b8d7d55_1/Containerfile && singularity push image.sif oras://repo:d4869cc39b8d7d55'
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
                format: BuildFormat.SINGULARITY)
        when:
        def cmd = service.buildCmd('build-job-name', req, testEnv)
        then:
        cmd == ['docker',
                'run',
                '--detach',
                '--name',
                'build-job-name',
                '--privileged',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '--platform', 'linux/arm64',
                'hrma017/singularity:v4.2.1-r5-2.4.13',
                'sh',
                '-c',
                'singularity build image.sif /fusion/s3/nextflow-ci/wave-build/workspace/bd-9c68af894bb2419c_1/Containerfile && singularity push image.sif oras://repo:9c68af894bb2419c'
        ]

        cleanup:
        ctx.close()
    }
}
