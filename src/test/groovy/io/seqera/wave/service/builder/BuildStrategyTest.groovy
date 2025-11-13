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
import io.seqera.wave.api.BuildCompression
import io.seqera.wave.api.BuildContext
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
        def containerId = ContainerHelper.makeContainerId(content, null, ContainerPlatform.of('amd64'), buildrepo, null, Mock(ContainerConfig))
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
                BuildCompression.gzip
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
                compression: new BuildCompression()
                        .withMode(REQ_COMPRESS as BuildCompression.Mode)
                        .withLevel(LEVEL)
                        .withForce(FORCE),
        )

        when:
        def config = new BuildConfig(
                ociMediatypes: true,
                compression: CONFIG_COMPRESS,
                forceCompression: FORCE,
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')
        and:
        def result = BuildStrategy.outputOpts(req, config)
        then:
        result == EXPECTED

        where:
        REQ_COMPRESS    | CONFIG_COMPRESS   | LEVEL | FORCE | EXPECTED
        null            | null              | null  | null  | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true'
        null            | 'gzip'            | null  | null  | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | null  | false | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip,force-compression=false'
        'gzip'          | null              | 10    | true  | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip,compression-level=10,force-compression=true'
        'gzip'          | 'estargz'         | null  | null  | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=gzip'
        and:
        null            | 'estargz'         | null  | null  | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,force-compression=true'
        'estargz'       | 'gzip'            | null  | null  | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,force-compression=true'
        and:
        null            | 'estargz'         | null  | false | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,force-compression=false'
        'estargz'       | 'gzip'            | null  | false | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,force-compression=false'
        and:
        null            | 'estargz'         | 1     | true | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,compression-level=1,force-compression=true'
        'estargz'       | 'gzip'            | 2     | true | 'type=image,name=quay.io/wave:c168dba125e28777,push=true,oci-mediatypes=true,compression=estargz,compression-level=2,force-compression=true'
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
                compression: new BuildCompression()
                        .withMode(REQ_COMPRESS as BuildCompression.Mode)
                        .withLevel(LEVEL)
                        .withForce(FORCE),
        )

        when:
        def config = new BuildConfig(
                ociMediatypes: true,
                compression: CONFIG_COMPRESS,
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')
        and:
        def result = BuildStrategy.cacheExportOpts(req, config)
        then:
        result == EXPECTED

        where:
        REQ_COMPRESS    | CONFIG_COMPRESS   | LEVEL | FORCE | EXPECTED
        null            | null              | null  | null | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true'
        null            | 'gzip'            | null  | null | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | null  | null | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | null              | null  | true  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=true'
        'gzip'          | null              | 10    | true  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,compression-level=10,force-compression=true'
        and:
        'gzip'          | 'estargz'         | null  | null  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip'
        'gzip'          | 'estargz'         | null  | true  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=gzip,force-compression=true'
        and:
        null            | 'estargz'         | null  | null  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,force-compression=true'   // <-- default to force compression when using 'estargz'
        'estargz'       | 'gzip'            | null  | null  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,force-compression=true'   // <-- default to force compression when using 'estargz'
        and:
        null            | 'estargz'         | null  | false | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,force-compression=false'
        'estargz'       | 'gzip'            | null  | false | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,force-compression=false'
        and:
        null            | 'estargz'         | 1     | true  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,compression-level=1,force-compression=true'
        'estargz'       | 'gzip'            | 2     | true  | 'type=registry,image-manifest=true,ref=reg.io/wave/build/cache:c168dba125e28777,mode=max,ignore-error=true,oci-mediatypes=true,compression=estargz,compression-level=2,force-compression=true'
    }

    @Unroll
    def 'should create S3 cache options' () {
        given:
        def req = new BuildRequest(
                containerId: 'abc123def456',
                buildId: 'bd-abc123def456_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:abc123def456',
                cacheRepository: S3_PATH,
                compression: new BuildCompression().withMode(COMPRESSION as BuildCompression.Mode)
        )

        when:
        def config = new BuildConfig(
                defaultCacheRepository: S3_PATH,
                cacheBucketRegion: REGION,
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')
        and:
        def result = BuildStrategy.s3ExportCacheOpts(req, config)

        then:
        result == EXPECTED

        where:
        S3_PATH                           | REGION      | COMPRESSION | EXPECTED
        's3://my-bucket/cache'            | 'us-east-1' | null        | 'type=s3,region=us-east-1,bucket=my-bucket,prefix=cache,name=abc123def456,mode=max,ignore-error=true'
        's3://my-bucket/cache/prefix'     | 'us-west-2' | null        | 'type=s3,region=us-west-2,bucket=my-bucket,prefix=cache/prefix,name=abc123def456,mode=max,ignore-error=true'
        's3://wave-cache/buildkit'        | 'eu-west-1' | 'gzip'      | 'type=s3,region=eu-west-1,bucket=wave-cache,prefix=buildkit,name=abc123def456,mode=max,ignore-error=true,compression=gzip'
        's3://my-bucket'                  | 'us-east-1' | null        | 'type=s3,region=us-east-1,bucket=my-bucket,name=abc123def456,mode=max,ignore-error=true'
    }

    def 'should create S3 import cache options' () {
        given:
        def req = new BuildRequest(
                containerId: 'xyz789abc123',
                buildId: 'bd-xyz789abc123_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:xyz789abc123',
                cacheRepository: 's3://test-bucket/cache/path'
        )

        when:
        def config = new BuildConfig(
                defaultCacheRepository: 's3://test-bucket/cache/path',
                cacheBucketRegion: 'ap-south-1',
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')
        and:
        def result = BuildStrategy.s3ImportCacheOpts(req, config)

        then:
        result == 'type=s3,region=ap-south-1,bucket=test-bucket,prefix=cache/path,name=xyz789abc123'
    }

    def 'should parse S3 bucket from path' () {
        expect:
        BuildStrategy.parseBucketFromS3Path(PATH) == BUCKET

        where:
        PATH                              | BUCKET
        's3://my-bucket/path/to/cache'    | 'my-bucket'
        's3://wave-cache'                 | 'wave-cache'
        's3://prod-bucket/deep/path'      | 'prod-bucket'
    }

    def 'should parse S3 prefix from path' () {
        expect:
        BuildStrategy.parsePrefixFromS3Path(PATH) == PREFIX

        where:
        PATH                              | PREFIX
        's3://my-bucket/path/to/cache'    | 'path/to/cache'
        's3://wave-cache/buildkit'        | 'buildkit'
        's3://prod-bucket'                | null
    }

    def 'should detect S3 cache repository' () {
        given:
        def config = new BuildConfig(
                defaultCacheRepository: 's3://my-bucket/cache',
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')

        expect:
        config.isCacheS3() == true
    }

    def 'should detect registry cache repository' () {
        given:
        def config = new BuildConfig(
                defaultCacheRepository: 'registry.example.com/wave/cache',
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')

        expect:
        config.isCacheS3() == false
    }

    def 'should create registry import cache options' () {
        given:
        def req = new BuildRequest(
                containerId: 'abc123def456',
                buildId: 'bd-abc123def456_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:abc123def456',
                cacheRepository: 'reg.io/wave/build/cache'
        )
        and:
        def config = new BuildConfig(
                defaultCacheRepository: 'reg.io/wave/build/cache',
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')

        when:
        def result = BuildStrategy.registryImportCacheOpts(req, config)

        then:
        result == 'type=registry,ref=reg.io/wave/build/cache:abc123def456'
    }

    @Unroll
    def 'should create cache import options for registry' () {
        given:
        def req = new BuildRequest(
                containerId: 'test123',
                buildId: 'bd-test123_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:test123',
                cacheRepository: CACHE_REPO
        )
        and:
        def config = new BuildConfig(
                defaultCacheRepository: CACHE_REPO,
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')

        when:
        def result = BuildStrategy.cacheImportOpts(req, config)

        then:
        result == EXPECTED

        where:
        CACHE_REPO                          | EXPECTED
        'reg.io/wave/cache'                 | 'type=registry,ref=reg.io/wave/cache:test123'
        'docker.io/library/cache'           | 'type=registry,ref=docker.io/library/cache:test123'
    }

    @Unroll
    def 'should create cache import options for S3' () {
        given:
        def req = new BuildRequest(
                containerId: 's3test456',
                buildId: 'bd-s3test456_1',
                workspace: Path.of('/work/foo'),
                platform: ContainerPlatform.of('linux/amd64'),
                targetImage: 'quay.io/wave:s3test456',
                cacheRepository: S3_PATH
        )
        and:
        def config = new BuildConfig(
                defaultCacheRepository: S3_PATH,
                cacheBucketRegion: REGION,
                buildkitImage: 'moby/buildkit:v0.26.0-rootless')

        when:
        def result = BuildStrategy.cacheImportOpts(req, config)

        then:
        result == EXPECTED

        where:
        S3_PATH                             | REGION      | EXPECTED
        's3://my-bucket/cache'              | 'us-east-1' | 'type=s3,region=us-east-1,bucket=my-bucket,prefix=cache,name=s3test456'
        's3://wave-cache/buildkit/prod'     | 'eu-west-1' | 'type=s3,region=eu-west-1,bucket=wave-cache,prefix=buildkit/prod,name=s3test456'
        's3://test-bucket'                  | 'ap-south-1'| 'type=s3,region=ap-south-1,bucket=test-bucket,name=s3test456'
    }
}
