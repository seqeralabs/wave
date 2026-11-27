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

package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.http.HttpStatus
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.tower.RecordOwner

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RecordAccessGuardTest extends Specification {

    static final RecordOwner OWNER =
            new RecordOwner(towerEndpoint: 'https://platform.io/api', userId: 1L, workspaceId: 10L)

    private RecordAccessGuard guard(RecordAccessService.Decision decision = RecordAccessService.Decision.ALLOW) {
        new RecordAccessGuard(accessService: Mock(RecordAccessService) { check(_, _, _) >> decision })
    }

    def 'should refuse a bearer meant for a different Platform without forwarding it' () {
        given:
        def access = Mock(RecordAccessService)
        def g = new RecordAccessGuard(accessService: access)

        when:
        def resp = g.refuse(OWNER, 'private', 'Bearer tok', 'https://other.io/api')

        then: 'the token never leaves Wave'
        0 * access.check(_, _, _)
        resp.status() == HttpStatus.UNAUTHORIZED
    }

    def 'should forward a bearer when the caller names the record owner' () {
        expect:
        guard().refuse(OWNER, 'private', 'Bearer tok', 'https://platform.io/api') == null
        and: 'a trailing slash is not a different Platform'
        guard().refuse(OWNER, 'private', 'Bearer tok', 'https://platform.io/api/') == null
    }

    def 'should forward a bearer when the caller names nothing, as released clients do' () {
        expect:
        guard().refuse(OWNER, 'private', 'Bearer tok', null) == null
    }

    def 'should decide nothing for a public record' () {
        given:
        def access = Mock(RecordAccessService)

        when:
        def resp = new RecordAccessGuard(accessService: access).refuse(OWNER, 'public', null)

        then: 'a public record admits everyone, so nothing is looked up at all'
        0 * access.check(_, _, _)
        resp == null
    }

}
