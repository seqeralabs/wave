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

import io.seqera.wave.api.BuildContext
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
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.DOCKER, Mock(PlatformId), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
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
        def build = Mock(BuildContext) {tarDigest >> '123'}
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.DOCKER, Mock(PlatformId), null, build, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
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
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.SINGULARITY, Mock(PlatformId), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                "sh",
                "-c",
                "singularity build image.sif /work/foo/c168dba125e28777/Containerfile && singularity push image.sif oras://quay.io/wave:c168dba125e28777"
            ]
    }

    def 'should get kaniko command with labels' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        service.@buildConfig = new BuildConfig()
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.DOCKER, Mock(PlatformId), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def labels = [
                "arch": "arm64",
                "packageName": "salmon",
                "version": "1.0.0"
        ]

        REQ.withLabels(labels)

        when:
        def cmd = service.launchCmd(REQ)
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
                '--label',
                'arch=arm64',
                '--label',
                'packageName=salmon',
                '--label',
                'version=1.0.0'
        ]
    }

}
