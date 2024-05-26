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

package io.seqera.wave.tower

import spock.lang.Specification

import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.SubmitContainerTokenRequest

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PlatformIdTest extends Specification {

    def 'should validate groovy truth' (){
        expect:
        !new PlatformId()
        !PlatformId.NULL
        and:
        new PlatformId(new User(id:1))
        new PlatformId(null, 1)
        new PlatformId(null, null, 'foo')
        new PlatformId(null, null, null, 'foo')
        new PlatformId(null, null, null, null, 'xyz')
    }

    def 'should create form a container request' () {
        when:
        def id = PlatformId.of(new User(id:1), new SubmitContainerTokenRequest(
                towerWorkspaceId: 100,
                towerEndpoint: 'http://foo.com',
                towerAccessToken: 'token-123',
                towerRefreshToken: 'refresh-123'))
        then:
        id.userId == 1
        id.user.id == 1
        id.workspaceId == 100
        id.towerEndpoint == 'http://foo.com'
        id.accessToken == 'token-123'
    }

    def 'should create form a inspect request' () {
        when:
        def id = PlatformId.of(new User(id:2), new ContainerInspectRequest(
                towerWorkspaceId: 100,
                towerEndpoint: 'http://foo.com',
                towerAccessToken: 'token-123' ))
        then:
        id.userId == 2
        id.user.id == 2
        id.workspaceId == 100
        id.towerEndpoint == 'http://foo.com'
        id.accessToken == 'token-123'
    }

    def 'should compute unique key' () {
        given:
        def id0 = new PlatformId()
        def id1 = new PlatformId(new User(id:100))
        def id2 = new PlatformId(new User(id:200))
        def id3 = new PlatformId(new User(id:100))
        
        expect:
        id1 != id2
        id3 == id1
        and:
        id1.hashCode() != id2.hashCode()
        id3.hashCode() == id1.hashCode()
    }
}
