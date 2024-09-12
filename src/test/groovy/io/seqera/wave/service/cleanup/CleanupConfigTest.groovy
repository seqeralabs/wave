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

package io.seqera.wave.service.cleanup

import spock.lang.Specification

import java.time.Duration

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CleanupConfigTest extends Specification {

    def 'should get random delay' () {
        when:
        def d = Duration.ofSeconds(10)
        def config = new CleanupConfig(cleanupStartupDelay: d)
        then:
        config.cleanupStartupDelay == d
        and:
        config.cleanupStartupDelayRandomized >= d.dividedBy(2)
        config.cleanupStartupDelayRandomized < (d + d.dividedBy(2))
    }

}
