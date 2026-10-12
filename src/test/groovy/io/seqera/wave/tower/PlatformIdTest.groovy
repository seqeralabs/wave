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
    }

    def 'should create form a container request' () {
        when:
        def id = PlatformId.of(new User(id:1, email: 'p@foo.com'), new SubmitContainerTokenRequest(
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
        id.userEmail == 'p@foo.com'
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

    def 'should return stable hash' () {
        given:
        def id1 = PlatformId.of(new User(id:1, email: 'p@foo.com'), Mock(SubmitContainerTokenRequest))

        def id2 = PlatformId.of(new User(id:2, email: 'p@foo.com'), Mock(SubmitContainerTokenRequest))

        def id3 = PlatformId.of(new User(id:1, email: 'p@foo.com'), new SubmitContainerTokenRequest(
                towerEndpoint: 'http://foo.com',
                towerAccessToken: 'token-123',
                towerRefreshToken: 'refresh-123',
                towerWorkspaceId: 100 ))
        and:
        def id4 = PlatformId.of(new User(id:1, email: 'p@foo.com'), new SubmitContainerTokenRequest(
                towerEndpoint: 'http://bar.com', // <-- change endpoint
                towerAccessToken: 'token-123',
                towerRefreshToken: 'refresh-123',
                towerWorkspaceId: 100 ))
        and:
        def id5 = PlatformId.of(new User(id:1, email: 'p@foo.com'), new SubmitContainerTokenRequest(
                towerEndpoint: 'http://foo.com',
                towerAccessToken: 'token-789',  // <-- change token
                towerRefreshToken: 'refresh-123',
                towerWorkspaceId: 100 ))

        def id6 = PlatformId.of(new User(id:1, email: 'p@foo.com'), new SubmitContainerTokenRequest(
                towerEndpoint: 'http://foo.com',
                towerAccessToken: 'token-123',
                towerRefreshToken: 'refresh-xxx',   // <-- change refresh, does not affect cache
                towerWorkspaceId: 100 ))

        def id7 = PlatformId.of(new User(id:1, email: 'p@foo.com'), new SubmitContainerTokenRequest(
                towerEndpoint: 'http://foo.com',
                towerAccessToken: 'token-123',
                towerRefreshToken: 'refresh-123',
                towerWorkspaceId: 200 ))        // <-- change workspace id
     
        expect:
        id1.stableHash() == 'a81eac1325c75af4'
        and:
        id2.stableHash() == '0bdd37bce6961402'
        and:
        id3.stableHash() == '0a630e69cd59db4e'
        and:
        id4.stableHash() == 'bf4cd9423edd1a4e'
        and:
        id5.stableHash() == 'b1977315b3edd1fc'
        and:
        id6.stableHash() == '0a630e69cd59db4e'
        and:
        id7.stableHash() == 'bb346b2662dc1696'
    }

}
