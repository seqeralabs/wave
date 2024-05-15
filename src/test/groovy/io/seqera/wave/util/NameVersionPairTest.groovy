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

package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class NameVersionPairTest extends Specification {

    @Unroll
    def 'should join both names and version' () {
        expect:
        new NameVersionPair(NAMES,VERS).qualifiedNames() == EXPECTED

        where:
        NAMES                       | VERS                  | EXPECTED
        ['foo']                     | ['1.0']               | 'foo-1.0'
        ['alpha','delta', 'gamma']  | ['1.0',null,'3.0']    | 'alpha-1.0_delta_gamma-3.0'
        ['alpha','delta', 'gamma']  | ['1.0']                   | 'alpha-1.0_delta_gamma'
        ['a','b','c','d','e']       | ['1','2','3','4','5']     | 'a-1_b-2_c-3_d-4_e-5'
        ['a','b','c','d','e','f']   | ['1','2','3','4','5','6'] | 'a-1_b-2_c-3_d-4_pruned'
    }

    @Unroll
    def 'should join names up to three' () {
        expect:
        new NameVersionPair(NAMES,VERS).friendlyNames() == EXPECTED

        where:
        NAMES                       | VERS                  | EXPECTED
        ['foo']                     | ['1.0']               | 'foo'
        ['alpha','delta', 'gamma']  | ['1.0',null,'3.0']    | 'alpha_delta_gamma'
        ['alpha','delta', 'gamma']  | ['1.0']               | 'alpha_delta_gamma'
        ['a','b','c','d','e']       | ['1','2','3','4','5'] | 'a_b_c_d_e'
        ['a','b','c','d','e','f']   | ['1','2','3','4','5'] | 'a_b_c_d_pruned'
    }

}
