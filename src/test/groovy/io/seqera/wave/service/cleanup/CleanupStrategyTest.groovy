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
import spock.lang.Unroll
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CleanupStrategyTest extends Specification {

    @Unroll
    def 'should validate cleanup rule' () {
        given:
        def strategy = new CleanupStrategy(config: new CleanupConfig(strategy: CONFIG), debugMode: DEBUG)

        expect:
        strategy.shouldCleanup(STATUS) == EXPECTED
        
        where:
        STATUS  | CONFIG        | DEBUG | EXPECTED
        0       | null          | false | true
        1       | null          | false | true
        and:
        0       | null          | true  | false
        1       | null          | true  | false
        and:
        0       | 'always'      | false | true
        0       | 'never'       | false | false
        0       | 'onsuccess'   | false | true
        1       | 'always'      | false | true
        1       | 'never'       | false | false
        1       | 'onsuccess'   | false | false
        and:
        0       | 'always'      | true | true
        0       | 'never'       | true | false
        0       | 'onsuccess'   | true | true
        1       | 'always'      | true | true
        1       | 'never'       | true | false
        1       | 'onsuccess'   | true | false
    }
}
