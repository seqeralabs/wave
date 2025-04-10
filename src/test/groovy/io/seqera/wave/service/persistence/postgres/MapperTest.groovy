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

package io.seqera.wave.service.persistence.postgres

import spock.lang.Specification

import groovy.transform.Canonical
import io.seqera.wave.service.persistence.PostgresIgnore

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MapperTest extends Specification {

    @Canonical
    static class Foo {
        String one
        Integer two
        List<String> three
    }

    @Canonical
    static class Bar {
        String one
        @PostgresIgnore Integer two
        @PostgresIgnore List<String> three
    }

    def 'should serialize and deserialize an object with ignore' () {
        given:
        def foo = new Foo(one: 'one', two: 2, three: ['a', 'b', 'c'])
        def bar = new Bar(one: 'one', two: 2, three: ['a', 'b', 'c'])

        when:
        def json1 = Mapper.toJson(foo)
        def json2 = Mapper.toJson(bar)

        then:
        json1 == '{"one":"one","two":2,"three":["a","b","c"]}'
        json2 == '{"one":"one"}'

    }

    def 'should merge objects' () {
        given:
        def o1 = '{"one":"one","two":2}'
        def o2 = '{"three":["a","b","c"]}'

        when:
        def result = Mapper.fromJson(Foo, o1, o2)
        then:
        result == new Foo(one: 'one', two: 2, three: ['a', 'b', 'c'])

    }

    def 'should merge objects with map' () {
        given:
        def o1 = '{"one":"one"}'
        def o2 = [two: 2, three: ['a', 'b', 'c']]

        when:
        def result = Mapper.fromJson(Foo, o1, o2)
        then:
        result == new Foo(one: 'one', two: 2, three: ['a', 'b', 'c'])
    }
}
