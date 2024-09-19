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
import java.time.Duration

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.ContainerHelper
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class BuildStrategyTest extends Specification {

    @Inject
    BuildStrategy strategy

    def 'should get buildkit command' () {
        given:
        def req = new BuildRequest(
                containerId: 'c168dba125e28777',
                workDir: Path.of('/work/foo/c168dba125e28777'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:c168dba125e28777',
                cacheRepository: 'reg.io/wave/build/cache' )

        when:
        def cmd = strategy.launchCmd(req)
        then:
        cmd == [
                'build',
                '--frontend',
                'dockerfile.v0',
                '--local',
                'dockerfile=/work/foo/c168dba125e28777',
                '--opt',
                'filename=Containerfile',
                '--local',
                'context=/work/foo/c168dba125e28777/context',
                '--output',
                'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true',
                '--opt',
                'platform=linux/amd64',
                '--export-cache',
                'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=false',
                '--import-cache',
                'type=registry,ref=reg.io/wave/build/cache:c168dba125e28777'
        ]
    }

    def 'should get buildkit command with build context' () {
        given:
        def req = new BuildRequest(
                containerId: 'c168dba125e28777',
                workDir: Path.of('/work/foo/3980470531b4a52a'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:3980470531b4a52a',
                cacheRepository: 'reg.io/wave/build/cache' )
        
        when:
        def cmd = strategy.launchCmd(req)
        then:
        cmd == [
                'build',
                '--frontend',
                'dockerfile.v0',
                '--local',
                'dockerfile=/work/foo/3980470531b4a52a',
                '--opt',
                'filename=Containerfile',
                '--local',
                'context=/work/foo/3980470531b4a52a/context',
                '--output',
                'type=image,name=quay.io/wave:3980470531b4a52a,push=true,oci-mediatypes=true',
                '--opt',
                'platform=linux/amd64',
                '--export-cache',
                'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=false',
                '--import-cache',
                'type=registry,ref=reg.io/wave/build/cache:c168dba125e28777'
        ]
    }

    def 'should get singularity command' () {
        given:
        def req = new BuildRequest(
                workDir: Path.of('/work/foo/c168dba125e28777'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'oras://quay.io/wave:c168dba125e28777',
                format: BuildFormat.SINGULARITY,
                cacheRepository: 'reg.io/wave/build/cache' )
        when:
        def cmd = strategy.launchCmd(req)
        then:
        cmd == [
                "sh",
                "-c",
                "singularity build image.sif /work/foo/c168dba125e28777/Containerfile && singularity push image.sif oras://quay.io/wave:c168dba125e28777"
            ]
    }

    def 'should create request' () {
        when:
        def timeout = Duration.ofMinutes(5)
        def content = 'FROM foo:latest'
        def workspace = Path.of("some/path")
        def buildrepo = 'foo.com/repo'
        def containerId = ContainerHelper.makeContainerId(content, null, ContainerPlatform.of('amd64'), buildrepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildrepo, containerId, null, null)
        def build = new BuildRequest(
                containerId,
                content,
                null,
                workspace,
                targetImage,
                PlatformId.NULL,
                ContainerPlatform.of('amd64'),
                'caherepo',
                "1.2.3.4",
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                null,
                BuildFormat.DOCKER,
                timeout
        )

        then:
        build.containerId == 'af15cb0a413a2d48'
        build.workspace == Path.of("some/path")
        and:
        !build.buildId
        !build.workDir

        when:
        build.withBuildId('100')
        then:
        build.containerId == 'af15cb0a413a2d48'
        build.workspace == Path.of("some/path")
        and:
        build.buildId == 'af15cb0a413a2d48_100'
        build.workDir == Path.of('.').toRealPath().resolve('some/path/af15cb0a413a2d48_100')
        build.maxDuration == timeout
    }
}
