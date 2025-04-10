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
import spock.lang.Unroll

import java.nio.file.Path
import java.time.Duration

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.CompressionMode
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.configuration.BuildConfig
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
                buildId: 'bd-c168dba125e28777_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:c168dba125e28777',
                cacheRepository: 'reg.io/wave/build/cache',
        )

        when:
        def cmd = strategy.launchCmd(req)
        then:
        cmd == [
                'build',
                '--frontend',
                'dockerfile.v0',
                '--local',
                'dockerfile=/work/foo/bd-c168dba125e28777_1',
                '--opt',
                'filename=Containerfile',
                '--local',
                'context=/work/foo/bd-c168dba125e28777_1/context',
                '--output',
                'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true',
                '--opt',
                'platform=linux/amd64',
                '--export-cache',
                'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true',
                '--import-cache',
                'type=registry,ref=reg.io/wave/build/cache:c168dba125e28777'
        ]
    }

    def 'should get buildkit command with build context' () {
        given:
        def req = new BuildRequest(
                containerId: 'c168dba125e28777',
                buildId: 'bd-c168dba125e28777_1',
                workspace: Path.of('/work/foo'),
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
                'dockerfile=/work/foo/bd-c168dba125e28777_1',
                '--opt',
                'filename=Containerfile',
                '--local',
                'context=/work/foo/bd-c168dba125e28777_1/context',
                '--output',
                'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true',
                '--opt',
                'platform=linux/amd64',
                '--export-cache',
                'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true',
                '--import-cache',
                'type=registry,ref=reg.io/wave/build/cache:c168dba125e28777'
        ]
    }

    def 'should get singularity command' () {
        given:
        def req = new BuildRequest(
                containerId: 'c168dba125e28777',
                buildId: 'bd-c168dba125e28777_1',
                workspace: Path.of('/work/foo'),
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
                "singularity build image.sif /work/foo/bd-c168dba125e28777_1/Containerfile && singularity push image.sif oras://quay.io/wave:c168dba125e28777"
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
                'condaFile',
                workspace,
                targetImage,
                PlatformId.NULL,
                ContainerPlatform.of('amd64'),
                'caherepo',
                "1.2.3.4",
                '{"config":"json"}',
                'GMT+1',
                Mock(ContainerConfig),
                'sc-12345',
                Mock(BuildContext),
                BuildFormat.DOCKER,
                timeout,
                CompressionMode.gzip
        )

        then:
        build.containerId == 'af15cb0a413a2d48'
        build.workspace == Path.of("some/path")
        and:
        build.containerId == 'af15cb0a413a2d48'
        build.workspace == Path.of("some/path")
        and:
        build.buildId == 'bd-af15cb0a413a2d48_0'
        build.workDir == Path.of('.').toRealPath().resolve('some/path/bd-af15cb0a413a2d48_0')
        build.maxDuration == timeout
    }

    @Unroll
    def 'should create output options' () {
        given:
        def req = new BuildRequest(
                containerId: 'c168dba125e28777',
                buildId: 'bd-c168dba125e28777_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:c168dba125e28777',
                cacheRepository: 'reg.io/wave/build/cache',
                compressionMode: REQ_COMPRESS,
        )

        when:
        def config = new BuildConfig(
                ociMediatypes: true,
                compression: CONFIG_COMPRESS,
                forceCompression: FORCE,
                buildkitImage: 'moby/buildkit:v0.18.2-rootless')
        and:
        def result = BuildStrategy.outputOpts(req, config)
        then:
        result == EXPECTED

        where:
        REQ_COMPRESS    | CONFIG_COMPRESS   | FORCE     | EXPECTED
        null            | null              | false     | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true'
        null            | 'gzip'            | false     | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | false     | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | true      | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip,force-compression=true'
        'gzip'          | 'estargz'         | true      | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip,force-compression=true'
        null            | 'estargz'         | true      | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,force-compression=true'
        'estargz'       | 'gzip'            | true      | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,force-compression=true'
    }


    @Unroll
    def 'should create cache options' () {
        given:
        def req = new BuildRequest(
                containerId: 'c168dba125e28777',
                buildId: 'bd-c168dba125e28777_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:c168dba125e28777',
                cacheRepository: 'reg.io/wave/build/cache',
                compressionMode: REQ_COMPRESS,
        )

        when:
        def config = new BuildConfig(
                ociMediatypes: true,
                compression: CONFIG_COMPRESS,
                forceCompression: FORCE,
                buildkitImage: 'moby/buildkit:v0.18.2-rootless')
        and:
        def result = BuildStrategy.cacheOpts(req, config)
        then:
        result == EXPECTED

        where:
        REQ_COMPRESS    | CONFIG_COMPRESS   | FORCE     | EXPECTED
        null            | null              | false     | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true'
        null            | 'gzip'            | false     | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | false     | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | true      | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=true'
        'gzip'          | 'estargz'         | true      | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=true'
        null            | 'estargz'         | true      | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,force-compression=true'
        'estargz'       | 'gzip'            | true      | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,force-compression=true'
    }
}
