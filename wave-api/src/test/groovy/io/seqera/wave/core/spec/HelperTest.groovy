/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.core.spec

import groovy.transform.Canonical
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class HelperTest extends Specification {

    def 'should convert value to integer' () {
        expect:
        Helper.asInteger(VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | null
        '100'       | 100i
        100l         | 100i
        100i        | 100i
        100d        | 100i
    }

    def 'should convert value to long' () {
        expect:
        Helper.asLong(VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | null
        '100'       | 100L
        100         | 100L
        100i        | 100L
        100d        | 100L
    }

    def 'should convert value to boolean' () {
        expect:
        Helper.asBoolean(VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | null
        'true'      | true
        'false'     | false
        true        | true
        false       | false
    }

    @Canonical
    static class Holder {
        Integer foo
        String bar
    }

    def 'should convert json to object'() {
        given:
        def JSON = '{"foo":123, "bar":"hello"}'
        expect:
        Helper.fromJson(JSON, Map) == [foo:123, bar:'hello']
        Helper.fromJson(JSON, Holder) == new Holder(123, 'hello')
    }
}
