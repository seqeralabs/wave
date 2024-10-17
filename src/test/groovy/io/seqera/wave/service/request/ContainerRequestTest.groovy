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

package io.seqera.wave.service.request

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ScanLevel
import io.seqera.wave.api.ScanMode
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

import io.seqera.wave.service.request.ContainerRequest.Type

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerRequestTest extends Specification {

    def 'should return request identity' () {
        given:
        ContainerRequest req

        when:
        req = ContainerRequest.of(new PlatformId(new User(id:1)))
        then:
        req.identity
        req.identity == new PlatformId(new User(id:1))
    }

    @Unroll
    def 'should validate constructor' () {
        when:
        def ts = Instant.now()
        def cfg = Mock(ContainerConfig)
        def req = new ContainerRequest(
                ContainerRequest.Type.Container,
                'r-1234',
                new PlatformId(new User(id:1)),
                'foo',
                'from docker',
                cfg,
                'conda file',
                ContainerPlatform.DEFAULT,
                'build-12345',
                BUILD_NEW,
                FREEZE,
                'scan-1234',
                ScanMode.required,
                List.of(ScanLevel.HIGH),
                DRY_RUN,
                SUCCEEDED,
                ts
        )
        then:
        req.type == ContainerRequest.Type.Container
        req.requestId == 'r-1234'
        req.identity == new PlatformId(new User(id:1))
        req.containerImage == 'foo'
        req.containerFile == 'from docker'
        req.containerConfig == cfg
        req.condaFile == 'conda file'
        req.platform == ContainerPlatform.DEFAULT
        req.buildId == 'build-12345'
        req.buildNew == BUILD_NEW
        req.freeze == FREEZE
        req.scanId == 'scan-1234'
        req.scanMode == ScanMode.required
        req.scanLevels == List.of(ScanLevel.HIGH)
        req.dryRun == DRY_RUN
        req.creationTime == ts

        where:
        BUILD_NEW | FREEZE | DRY_RUN | SUCCEEDED
        true      | false  | false   | false
        false     | true   | false   | false
        false     | false  | true    | false
        false     | false  | false   | true
    }

    @Unroll
    def 'should validate durable flag' () {
        given:
        def req = ContainerRequest.of(
                identity: new PlatformId(new User(id:1)),
                freeze: FREEZE,
                type: TYPE )

        expect:
        req.durable() == EXPECTED

        where:
        FREEZE  | TYPE          | EXPECTED
        false   | Type.Build    | false
        true    | Type.Build    | true
        false   | Type.Mirror   | true
        true    | Type.Mirror   | true
    }

    def 'should validate mirror flag' () {
        given:
        def req = ContainerRequest.of(type: TYPE)
        expect:
        req.getMirror() == EXPECTED

        where:
        TYPE            | EXPECTED
        Type.Container  | false
        Type.Build      | false
        Type.Mirror     | true
    }

    def 'should validate container request flag' () {
        given:
        def req = ContainerRequest.of(type: TYPE)
        expect:
        req.isContainer() == EXPECTED

        where:
        TYPE            | EXPECTED
        Type.Container  | true
        Type.Build      | false
        Type.Mirror     | false
    }
}
