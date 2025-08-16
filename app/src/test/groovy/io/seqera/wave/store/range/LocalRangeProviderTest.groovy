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

package io.seqera.wave.store.range

import spock.lang.Specification

import io.seqera.wave.store.range.impl.LocalRangeProvider
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalRangeProviderTest extends Specification {


    def 'should add and get elements' () {
        given:
        def provider = new LocalRangeProvider()
        and:
        provider.add('foo', 'x', 1)
        provider.add('foo', 'y', 2)
        provider.add('foo', 'z', 3)
        provider.add('bar', 'z', 10)

        expect:
        provider.getRange('foo', 0,1, 10, false)
                == ['x']
        and:
        provider.getRange('foo', 1,1, 10, false)
                == ['x']
        and:
        provider.getRange('foo', 1,2, 10, false)
                == ['x','y']
        and:
        provider.getRange('foo', 1,3, 10, false)
                == ['x','y','z']
        and:
        provider.getRange('foo', 1.1,3, 10, false)
                == ['y','z']
        and:
        provider.getRange('foo', 1,3, 1, false)
                == ['x']

        and:
        provider.getRange('bar', 1,3, 1, false)
                == []
        and:
        provider.getRange('bar', 10,10, 1, false)
                == ['z']

        when:
        provider.add('bar', 'z', 20)
        then:
        provider.getRange('bar', 10,10, 1, false)
                == []
        and:
        provider.getRange('bar', 10,20, 1, false)
                == ['z']
        and:
        provider.getRange('bar', 1,100, 100, false)
                == ['z']

    }

    def 'should remove elements' () {
        given:
        def provider = new LocalRangeProvider()
        and:
        provider.add('foo', 'x', 1)
        provider.add('foo', 'y', 2)
        provider.add('foo', 'z', 3)
        provider.add('bar', 'z', 10)

        expect:
        provider.getRange('foo', 1,1, 1, true) == ['x']
        and:
        provider.getRange('foo', 1,10, 10, true) == ['y','z']
        and:
        provider.getRange('foo', 1,10, 10, true) == []

        and:
        provider.getRange('bar', 1,10, 10, true) == ['z']
        and:
        provider.getRange('bar', 1,10, 10, true) == []
    }
}
