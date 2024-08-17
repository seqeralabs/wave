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
import java.time.OffsetDateTime

import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.util.ContainerHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildRequestTest extends Specification {

    def 'should create docker build request'() {
        given:
        def TIMEOUT = Duration.ofMinutes(5)
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
        def PLATFORM = ContainerPlatform.of('amd64')
        def FORMAT = BuildFormat.DOCKER
        def CONTAINER_ID = ContainerHelper.makeContainerId(CONTENT, null, PLATFORM, BUILD_REPO, CONTEXT)
        def TARGET_IMAGE = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID, null, null)

        when:
        def req = new BuildRequest(
                CONTAINER_ID,
                CONTENT,
                null,
                PATH,
                TARGET_IMAGE,
                USER,
                PLATFORM,
                CACHE_REPO,
                IP_ADDR,
                '{"config":"json"}',
                OFFSET,
                CONFIG,
                SCAN_ID,
                CONTEXT,
                FORMAT,
                TIMEOUT
        )

        then:
        req.containerId == '181ec22b26ae6d04'
        req.targetImage == "docker.io/wave:${req.containerId}"
        req.containerFile == CONTENT
        req.identity == USER
        req.configJson == '{"config":"json"}'
        req.cacheRepository == CACHE_REPO
        req.format == BuildFormat.DOCKER
        req.condaFile == null
        req.platform == ContainerPlatform.of('amd64')
        req.configJson == '{"config":"json"}'
        req.scanId == SCAN_ID
        req.ip == IP_ADDR
        req.offsetId == OFFSET
        req.containerConfig == CONFIG
        req.buildContext == CONTEXT

        // ==== provide a Conda recipe ====
        when:
        def CONDA_RECIPE = '''\
                dependencies:
                    - samtools=1.0
                '''
        and:
        CONTAINER_ID = ContainerHelper.makeContainerId(CONTENT, CONDA_RECIPE, PLATFORM, BUILD_REPO, CONTEXT)
        TARGET_IMAGE = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID, CONDA_RECIPE, null)
        req = new BuildRequest(
                CONTAINER_ID,
                CONTENT,
                CONDA_RECIPE,
                PATH,
                TARGET_IMAGE,
                USER,
                PLATFORM,
                CACHE_REPO,
                IP_ADDR,
                '{"config":"json"}',
                OFFSET,
                CONFIG,
                SCAN_ID,
                CONTEXT,
                FORMAT,
                TIMEOUT
        )
        then:
        req.containerId == '8026e3a63b5c863f'
        req.targetImage == 'docker.io/wave:samtools-1.0--8026e3a63b5c863f'
        req.condaFile == CONDA_RECIPE

    }

    def 'should create singularity build request'() {
        given:
        def TIMEOUT = Duration.ofMinutes(5)
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def CONTENT = 'From: foo'
        def PATH = Path.of('somewhere')
        def BUILD_REPO = 'docker.io/wave'
        def CACHE_REPO = 'docker.io/cache'
        def IP_ADDR = '10.20.30.40'
        def OFFSET = '+2'
        def CONFIG = new ContainerConfig(env: ['FOO=1'])
        def CONTEXT = Mock(BuildContext)
        def PLATFORM = ContainerPlatform.of('amd64')
        def FORMAT = BuildFormat.SINGULARITY
        def CONTAINER_ID = ContainerHelper.makeContainerId(CONTENT, null, PLATFORM, BUILD_REPO, CONTEXT)
        def TARGET_IMAGE = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID, null, null)

        when:
        def req = new BuildRequest(
                CONTAINER_ID,
                CONTENT,
                null,
                PATH,
                TARGET_IMAGE,
                USER,
                PLATFORM,
                CACHE_REPO,
                IP_ADDR,
                '{"config":"json"}',
                OFFSET,
                CONFIG,
                null,
                CONTEXT,
                FORMAT,
                TIMEOUT
        )
        then:
        req.containerId == 'd78ba9cb01188668'
        req.targetImage == "oras://docker.io/wave:${req.containerId}"
        req.containerFile == CONTENT
        req.identity == USER
        req.configJson == '{"config":"json"}'
        req.cacheRepository == CACHE_REPO
        req.format == BuildFormat.SINGULARITY
        req.platform == ContainerPlatform.of('amd64')
        req.configJson == '{"config":"json"}'
        req.ip == IP_ADDR
        req.offsetId == OFFSET
        req.containerConfig == CONFIG
        req.buildContext == CONTEXT

    }

    def 'should check equals and hash code'() {
        given:
        def TIMEOUT = Duration.ofMinutes(5)
        def USER = new PlatformId(new User(id:1, email: 'foo@user.com'))
        def PATH = Path.of('somewhere')
        def BUILD_REPO = 'docker.io/wave'
        def CACHE_REPO = 'docker.io/cache'
        def PLATFORM = ContainerPlatform.of('amd64')
        def FORMAT = BuildFormat.DOCKER
        def CONDA_CONTENT = 'salmon=1.2.3'
        def FOO_CONTENT = 'from foo'
        def BAR_CONTENT = 'from bar'
        and:
        def CONTAINER_ID1 = ContainerHelper.makeContainerId(FOO_CONTENT, null, PLATFORM, BUILD_REPO, null)
        def TARGET_IMAGE1 = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID1, null, null)
        def req1 = new BuildRequest(CONTAINER_ID1, FOO_CONTENT, null, PATH, TARGET_IMAGE1, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', null, null, null, null, FORMAT, TIMEOUT)
        and:
        def req2 = new BuildRequest(CONTAINER_ID1, FOO_CONTENT, null, PATH, TARGET_IMAGE1, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', null, null, null, null, FORMAT, TIMEOUT)
        and:
        def CONTAINER_ID3 = ContainerHelper.makeContainerId(BAR_CONTENT, null, PLATFORM, BUILD_REPO, null)
        def TARGET_IMAGE3 = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID3, null, null)
        def req3 = new BuildRequest(CONTAINER_ID3, BAR_CONTENT, null, PATH, TARGET_IMAGE3, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', null, null, null, null, FORMAT, TIMEOUT)
        and:
        def CONTAINER_ID4 = ContainerHelper.makeContainerId(BAR_CONTENT, CONDA_CONTENT, PLATFORM, BUILD_REPO, null)
        def TARGET_IMAGE4 = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID4, CONDA_CONTENT, null)
        def req4 = new BuildRequest(CONTAINER_ID4, BAR_CONTENT, CONDA_CONTENT, PATH, TARGET_IMAGE4, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', null, null, null, null, FORMAT, TIMEOUT)
        and:
        def req5 = new BuildRequest(CONTAINER_ID4, BAR_CONTENT, CONDA_CONTENT, PATH, TARGET_IMAGE4, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', null, null, null, null, FORMAT, TIMEOUT)
        and:
        CONDA_CONTENT = 'salmon=1.2.5'
        def CONTAINER_ID6 = ContainerHelper.makeContainerId(BAR_CONTENT, CONDA_CONTENT, PLATFORM, BUILD_REPO, null)
        def TARGET_IMAGE6 = ContainerHelper.makeTargetImage(FORMAT, BUILD_REPO, CONTAINER_ID6, CONDA_CONTENT, null)
        def req6 = new BuildRequest(CONTAINER_ID4, BAR_CONTENT, CONDA_CONTENT, PATH, TARGET_IMAGE6, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', null, null, null, null, FORMAT, TIMEOUT)
        and:
        def req7 = new BuildRequest(CONTAINER_ID4, BAR_CONTENT, CONDA_CONTENT, PATH, TARGET_IMAGE6, USER, PLATFORM, CACHE_REPO, "10.20.30.40", '{"config":"json"}', "UTC+2", null, null, null, FORMAT, TIMEOUT)

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
