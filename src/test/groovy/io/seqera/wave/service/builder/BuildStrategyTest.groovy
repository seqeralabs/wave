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

import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildStrategyTest extends Specification {

    def 'should get kaniko command' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        service.@buildConfig = new BuildConfig()
        and:
        def req = new BuildRequest(
                workDir: Path.of('/work/foo/c168dba125e28777'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:c168dba125e28777',
                cacheRepository: 'reg.io/wave/build/cache' )

        when:
        def cmd = service.launchCmd(req)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/c168dba125e28777/Containerfile',
                '--context',
                '/work/foo/c168dba125e28777/context',
                '--destination',
                'quay.io/wave:c168dba125e28777',
                '--cache=true',
                '--custom-platform',
                'linux/amd64',
                '--cache-repo',
                'reg.io/wave/build/cache',
        ]
    }

    def 'should get kaniko command with build context' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        service.@buildConfig = new BuildConfig()
        and:
        def req = new BuildRequest(
                workDir: Path.of('/work/foo/3980470531b4a52a'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:3980470531b4a52a',
                cacheRepository: 'reg.io/wave/build/cache' )
        
        when:
        def cmd = service.launchCmd(req)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/3980470531b4a52a/Containerfile',
                '--context',
                '/work/foo/3980470531b4a52a/context',
                '--destination',
                'quay.io/wave:3980470531b4a52a',
                '--cache=true',
                '--custom-platform',
                'linux/amd64',
                '--cache-repo',
                'reg.io/wave/build/cache',
        ]
    }

    def 'should get singularity command' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        and:
        def req = new BuildRequest(
                workDir: Path.of('/work/foo/c168dba125e28777'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'oras://quay.io/wave:c168dba125e28777',
                format: BuildFormat.SINGULARITY,
                cacheRepository: 'reg.io/wave/build/cache' )
        when:
        def cmd = service.launchCmd(req)
        then:
        cmd == [
                "sh",
                "-c",
                "singularity build image.sif /work/foo/c168dba125e28777/Containerfile && singularity push image.sif oras://quay.io/wave:c168dba125e28777"
            ]
    }

    def 'should create request' () {
        when:
        def content = 'FROM foo:latest'
        def workspace = Path.of("some/path")
        def buildrepo = 'foo.com/repo'
        def containerId = BuildRequest.computeDigest(content, null, null, ContainerPlatform.of('amd64'), buildrepo, null)
        def targetImage = BuildRequest.makeTarget(BuildFormat.DOCKER, buildrepo, containerId, null, null, null)
        def build = new BuildRequest(
                containerId,
                content,
                null,
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
                BuildFormat.DOCKER
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
    }

}
