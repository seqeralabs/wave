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

package io.seqera.wave.service

import spock.lang.Specification

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
        req = new ContainerRequestData(new PlatformId(new User(id:1)))
        then:
        req.identity
        req.identity == new PlatformId(new User(id:1))

        when:
        req = new ContainerRequestData(null, null, null, null, null, null, null, null, null, 100)
        then:
        req.identity
        req.identity == new PlatformId(new User(id:100))

        when:
        req = new ContainerRequestData(null, null, null, null, null, null, null, null, null, null, null, null, 'http://foo.com')
        then:
        !req.identity
        req.identity == PlatformId.NULL
        req.towerEndpoint == 'http://foo.com'
    }

}
