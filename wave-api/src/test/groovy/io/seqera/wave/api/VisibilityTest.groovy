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

package io.seqera.wave.api

import com.squareup.moshi.Moshi
import spock.lang.Specification

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class VisibilityTest extends Specification {

    def 'should convert a wire value or a constant' () {
        expect:
        Visibility.of(VALUE) == EXPECTED

        where:
        VALUE               | EXPECTED
        null                | null
        'public'            | Visibility.PUBLIC
        'private'           | Visibility.PRIVATE
        'Private'           | Visibility.PRIVATE
        Visibility.PRIVATE  | Visibility.PRIVATE
    }

    def 'should reject an unknown value' () {
        when:
        Visibility.of(VALUE)
        then:
        thrown(IllegalArgumentException)

        where:
        VALUE << ['secret', '']
    }

    def 'should read only private as private' () {
        expect:
        Visibility.isPrivate(Visibility.PRIVATE)
        and:
        !Visibility.isPrivate(null)
        !Visibility.isPrivate(Visibility.PUBLIC)
    }

    def 'should use the lowercase wire value' () {
        expect:
        Visibility.PUBLIC.value() == 'public'
        Visibility.PRIVATE.toString() == 'private'
    }

    def 'should serialise with Moshi using the lowercase wire value' () {
        given:
        def adapter = new Moshi.Builder().build().adapter(Visibility)

        expect:
        adapter.toJson(Visibility.PRIVATE) == '"private"'
        adapter.toJson(Visibility.PUBLIC) == '"public"'
        adapter.fromJson('"private"') == Visibility.PRIVATE
        adapter.fromJson('"public"') == Visibility.PUBLIC
    }

}
