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

package io.seqera.wave.tower.auth

import spock.lang.Specification

import java.time.Duration
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JwtConfigTest extends Specification {

    def 'should get random delay' () {
        when:
        def d = Duration.ofSeconds(10)
        def config = new JwtConfig(monitorDelay: d)
        then:
        config.monitorDelay == d
        and:
        config.monitorDelayRandomized >= d.dividedBy(2)
        config.monitorDelayRandomized < (d + d.dividedBy(2))
    }

}
