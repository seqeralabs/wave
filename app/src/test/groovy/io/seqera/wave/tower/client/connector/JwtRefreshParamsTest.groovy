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

package io.seqera.wave.tower.client.connector

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.tower.auth.JwtAuth

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JwtRefreshParamsTest extends Specification {

    def 'should validate equals and hashcode' () {
        given:
        def now = Instant.now()
        def j1 = new JwtAuth('1', '2', '3', '4', now, now)
        def j2 = new JwtAuth('1', '2', '3', '4', now.plusSeconds(1), now.plusSeconds(2))
        and:
        def j3 = new JwtAuth('1', 'X', '3', '4', now, now)
        def j4 = new JwtAuth('1', '2', 'X', '4', now, now)
        def j5 = new JwtAuth('1', '2', '3', 'X', now, now)

        and:
        def p1 = new JwtRefreshParams('foo.com', j1)
        def p2 = new JwtRefreshParams('foo.com', j2)
        def p3 = new JwtRefreshParams('foo.com', j3)
        def p4 = new JwtRefreshParams('foo.com', j4)
        def p5 = new JwtRefreshParams('foo.com', j5)
        def p6 = new JwtRefreshParams('bar.com', j1)

        expect:
        p1 == p2
        and:
        p1 != p3
        p1 != p4
        p1 != p5
        p1 != p6

        and:
        p1.hashCode() == p2.hashCode()
        and:
        p1.hashCode() != p3.hashCode()
        p1.hashCode() != p4.hashCode()
        p1.hashCode() != p5.hashCode()
        p1.hashCode() != p6.hashCode()

    }

}
