/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RecordOwnerTest extends Specification {

    def 'should have no owner when the request is anonymous' () {
        expect:
        RecordOwner.of(null) == null
        RecordOwner.of(PlatformId.NULL) == null
    }

    def 'should have no owner when the identity carries an endpoint but no user' () {
        given:
        // ContainerController fills in the default endpoint before handling anonymous
        // requests, so an endpoint on its own does not make a record owned
        def identity = new PlatformId(null, null, null, 'https://api.cloud.seqera.io')

        expect:
        RecordOwner.of(identity) == null
    }

    def 'should take the owner from the request identity' () {
        given:
        def identity = new PlatformId(new User(id: 100), 200L, 'token', 'https://api.cloud.seqera.io')

        when:
        def owner = RecordOwner.of(identity)

        then:
        owner.userId == 100L
        owner.workspaceId == 200L
        owner.towerEndpoint == 'https://api.cloud.seqera.io'
    }

    def 'should keep the endpoint exactly as the request carried it' () {
        given:
        // the submit path only admits the spelling Platform paired with, and that is the
        // address the access check has to dial back - normalising it here could miss
        def owner = RecordOwner.of(new PlatformId(new User(id: 1), 2L, 'tok', 'https://api.cloud.seqera.io/'))

        expect:
        owner.towerEndpoint == 'https://api.cloud.seqera.io/'
    }

    def 'should carry no access token' () {
        given:
        def owner = RecordOwner.of(new PlatformId(new User(id: 1), 2L, 'secret-token', 'https://foo.com'))

        expect:
        !owner.properties.values().any { it == 'secret-token' }
    }

    def 'should keep two spellings of one host apart in the tenant' () {
        given: 'the same workspace reached through endpoints that differ only in spelling'
        def paired = new RecordOwner(towerEndpoint: 'https://api.cloud.seqera.io', userId: 1L, workspaceId: 10L)
        def impostor = new RecordOwner(towerEndpoint: 'https://API.cloud.seqera.io', userId: 1L, workspaceId: 10L)
        def ported = new RecordOwner(towerEndpoint: 'https://api.cloud.seqera.io:443', userId: 1L, workspaceId: 10L)
        def slashed = new RecordOwner(towerEndpoint: 'https://api.cloud.seqera.io/', userId: 1L, workspaceId: 10L)

        expect: 'anyone can pair a spelling, so folding them together would share the private ids'
        paired.tenant() != impostor.tenant()
        paired.tenant() != ported.tenant()
        paired.tenant() != slashed.tenant()

        and: 'the endpoint goes in exactly as paired'
        paired.tenant() == 'https://api.cloud.seqera.io:10'
    }

    def 'should separate a personal workspace from a numbered one' () {
        expect:
        new RecordOwner(towerEndpoint: 'https://foo.com/api', userId: 1L).tenant() == 'https://foo.com/api:user:1'
        and: 'workspace 0 is a workspace, not a missing one'
        new RecordOwner(towerEndpoint: 'https://foo.com/api', userId: 1L, workspaceId: 0L).tenant() == 'https://foo.com/api:0'
    }

}
