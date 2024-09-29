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

package io.seqera.wave.service.token

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ScanLevel
import io.seqera.wave.api.ScanMode
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerRequestDataTest extends Specification {

    def 'should return request identity' () {
        given:
        ContainerRequestData req

        when:
        req = ContainerRequestData.of(new PlatformId(new User(id:1)))
        then:
        req.identity
        req.identity == new PlatformId(new User(id:1))
    }

    def 'should validate constructor' () {
        when:
        def ts = Instant.now()
        def cfg = Mock(ContainerConfig)
        def req = new ContainerRequestData(
                'r-1234',
                new PlatformId(new User(id:1)),
                'foo',
                'from docker',
                cfg,
                'conda file',
                ContainerPlatform.DEFAULT,
                'build-12345',
                true,
                true,
                true,
                'scan-1234',
                ScanMode.sync,
                List.of(ScanLevel.high),
                ts
        )
        then:
        req.requestId == 'r-1234'
        req.identity == new PlatformId(new User(id:1))
        req.containerImage == 'foo'
        req.containerFile == 'from docker'
        req.containerConfig == cfg
        req.condaFile == 'conda file'
        req.platform == ContainerPlatform.DEFAULT
        req.buildId == 'build-12345'
        req.buildNew
        req.freeze
        req.mirror
        req.scanId == 'scan-1234'
        req.scanMode == ScanMode.sync
        req.scanLevels == List.of(ScanLevel.high)
        req.creationTime == ts

    }

    def 'should validate durable flag' () {
        given:
        def req = ContainerRequestData.of(
                identity: new PlatformId(new User(id:1)),
                freeze: FREEZE,
                mirror: MIRROR )

        expect:
        req.durable() == EXPECTED

        where:
        FREEZE  | MIRROR    | EXPECTED
        false   | false     | false
        true    | false     | true
        false   | true      | true
        true    | true      | true
    }

}
