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
import java.time.OffsetDateTime

import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildRequestTest extends Specification {

    def 'should create docker build request'() {
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def CONTENT = 'FROM foo'
        def PATH = Path.of('somewhere')
        def BUILD_REPO = 'docker.io/wave'
        def CACHE_REPO = 'docker.io/cache'
        def SCAN_ID = '123456'
        def IP_ADDR = '10.20.30.40'
        def OFFSET = '+2'
        def CONFIG = new ContainerConfig(env: ['FOO=1'])
        def CONTEXT = Mock(BuildContext)

        when:
        def req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                null,
                null,
                BuildFormat.DOCKER,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                SCAN_ID,
                IP_ADDR,
                OFFSET,
                null)
        then:
        req.containerId == '181ec22b26ae6d04'
        req.targetImage == "docker.io/wave:${req.containerId}"
        req.containerFile == CONTENT
        req.identity == USER
        req.configJson == '{auth}'
        req.cacheRepository == CACHE_REPO
        req.format == BuildFormat.DOCKER
        req.condaFile == null
        req.spackFile == null
        req.platform == ContainerPlatform.of('amd64')
        req.configJson == '{auth}'
        req.scanId == SCAN_ID
        req.ip == IP_ADDR
        req.offsetId == OFFSET
        req.containerConfig == CONFIG
        req.buildContext == CONTEXT
        and:
        !req.isSpackBuild

        // ==== provide a Conda recipe ====
        when:
        def CONDA_RECIPE = '''\
                dependencies:
                    - samtools=1.0
                '''
        and:
        req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                CONDA_RECIPE,
                null,
                BuildFormat.DOCKER,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                SCAN_ID,
                IP_ADDR,
                OFFSET,
                null)
        then:
        req.containerId == '8026e3a63b5c863f'
        req.targetImage == 'docker.io/wave:samtools-1.0--8026e3a63b5c863f'
        req.condaFile == CONDA_RECIPE
        req.spackFile == null
        and:
        !req.isSpackBuild

        // ===== spack content ====
        def SPACK_RECIPE = '''\
            spack:
              specs: [bwa@0.7.15]
            '''
        and:
        when:
        req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                null,
                SPACK_RECIPE,
                BuildFormat.DOCKER,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                SCAN_ID,
                IP_ADDR,
                OFFSET,
                null)
        then:
        req.containerId == '8726782b1d9bb8fb'
        req.targetImage == 'docker.io/wave:bwa-0.7.15--8726782b1d9bb8fb'
        req.spackFile == SPACK_RECIPE
        req.condaFile == null
        and:
        req.isSpackBuild
    }

    def 'should create singularity build request'() {
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def CONTENT = 'From: foo'
        def PATH = Path.of('somewhere')
        def BUILD_REPO = 'docker.io/wave'
        def CACHE_REPO = 'docker.io/cache'
        def IP_ADDR = '10.20.30.40'
        def OFFSET = '+2'
        def CONFIG = new ContainerConfig(env: ['FOO=1'])
        def CONTEXT = Mock(BuildContext)

        when:
        def req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                null,
                null,
                BuildFormat.SINGULARITY,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                null,
                IP_ADDR,
                OFFSET,
                null)
        then:
        req.containerId == 'd78ba9cb01188668'
        req.targetImage == "oras://docker.io/wave:${req.containerId}"
        req.containerFile == CONTENT
        req.identity == USER
        req.configJson == '{auth}'
        req.cacheRepository == CACHE_REPO
        req.format == BuildFormat.SINGULARITY
        req.platform == ContainerPlatform.of('amd64')
        req.configJson == '{auth}'
        req.ip == IP_ADDR
        req.offsetId == OFFSET
        req.containerConfig == CONFIG
        req.buildContext == CONTEXT
        and:
        !req.isSpackBuild

    }

    def 'should check equals and hash code'() {
        given:
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        and:
        def req1 = new BuildRequest('from foo', PATH, repo, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null, null)
        def req2 = new BuildRequest('from foo', PATH, repo, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null, null)
        def req3 = new BuildRequest('from bar', PATH, repo, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null, null)
        def req4 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null, null)
        def req5 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null, null)
        def req6 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null, null)
        def req7 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", "UTC+2", null)

        expect:
        req1 == req2
        req1 != req3
        and:
        req4 == req5
        req4 != req6
        and:
        req1 != req5
        req1 != req6
        req1 != req7

        and:
        req1.hashCode() == req2.hashCode()
        req1.hashCode() != req3.hashCode()
        and:
        req4.hashCode() == req5 .hashCode()
        req4.hashCode() != req6.hashCode()
        and:
        req1.hashCode() != req5.hashCode()
        req1.hashCode() != req6.hashCode()

        and:
        req1.offsetId == OffsetDateTime.now().offset.id
        req7.offsetId == 'UTC+2'
    }

    def 'should make request target' () {
        expect:
        BuildRequest.makeTarget(BuildFormat.DOCKER, 'quay.io/org/name', '12345', null, null, null)
                == 'quay.io/org/name:12345'
        and:
        BuildRequest.makeTarget(BuildFormat.SINGULARITY, 'quay.io/org/name', '12345', null, null, null)
                == 'oras://quay.io/org/name:12345'

        and:
        def conda = '''\
        dependencies:
        - salmon=1.2.3
        '''
        BuildRequest.makeTarget(BuildFormat.DOCKER, 'quay.io/org/name', '12345', conda, null, null)
                == 'quay.io/org/name:salmon-1.2.3--12345'

        and:
        def spack = '''\
         spack:
            specs: [bwa@0.7.15]
        '''
        BuildRequest.makeTarget(BuildFormat.DOCKER, 'quay.io/org/name', '12345', null, spack, null)
                == 'quay.io/org/name:bwa-0.7.15--12345'

        and: 'should return targetImage with provided custom container image name'
        BuildRequest.makeTarget(BuildFormat.DOCKER, 'quay.io/org/name', '12345', null, null, 'foo')
                == 'quay.io/org/name/foo:12345'

        and:
        BuildRequest.makeTarget(BuildFormat.DOCKER, 'quay.io/org', '12345', null, null, 'foo/bar')
                == 'quay.io/org/foo/bar:12345'

        and:
        BuildRequest.makeTarget(BuildFormat.DOCKER, 'quay.io', '12345', null, null, 'foo/bar')
                == 'quay.io/foo/bar:12345'

    }

    @Unroll
    def 'should normalise tag' () {
        expect:
        BuildRequest.normaliseTag(TAG,12)  == EXPECTED
        where:
        TAG                     | EXPECTED
        null                    | null
        ''                      | null
        and:
        'foo'                   | 'foo'
        'FOO123'                | 'FOO123'
        'aa-bb_cc.dd'           | 'aa-bb_cc.dd'
        and:
        'one(two)three'         | 'onetwothree'
        '12345_67890_12345'     | '12345_67890'
        '123456789012345_1'     | '123456789012'
        and:
        'aa__'                  | 'aa'
        'aa..--__'              | 'aa'
        '..--__bb'              | 'bb'
        '._-xyz._-'             | 'xyz'
    }


    def 'should parse legacy id' () {
        expect:
        BuildRequest.legacyBuildId(BUILD_ID) == EXPECTED
        where:
        BUILD_ID        | EXPECTED
        null            | null
        'foo'           | null
        'foo_01'        | 'foo'
    }

}
