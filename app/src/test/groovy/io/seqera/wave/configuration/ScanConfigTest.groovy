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

package io.seqera.wave.configuration

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanConfigTest extends Specification {

    def 'should convert env to tuples' () {
        given:
        def config1 = new ScanConfig()
        def config2 = new ScanConfig(environment: ['FOO=one','BAR=two'])
        def config3 = new ScanConfig(environment: ['FOO','BAR='])

        expect:
        config1.environmentAsTuples == []
        config2.environmentAsTuples == [new Tuple2('FOO','one'), new Tuple2('BAR','two')]
        config3.environmentAsTuples == [new Tuple2('FOO',''), new Tuple2('BAR','')]

    }
}
