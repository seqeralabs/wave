/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import spock.lang.Specification
/**
 * Test for the FusionVersionStringDeserializer.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class FusionVersionStringDeserializerTest extends Specification {

    def 'should deserialize string or extract number from object'() {
        given:
        def module = new SimpleModule()
        module.addDeserializer(String, new FusionVersionStringDeserializer())

        def mapper = new ObjectMapper()
        mapper.registerModule(module)

        expect:
        mapper.readValue(json, String) == expected

        where:
        json                                                             || expected
        '"https://example.com/fusion/v2.5.0-linux.json"'                 || 'https://example.com/fusion/v2.5.0-linux.json'
        '{ "number": "3.1.0", "arch": "arm64" }'                         || '3.1.0'
        '{ "number": "2.0.1" }'                                          || '2.0.1'
        '{ "arch": "x86" }'                                              || null
        'null'                                                           || null
    }
}

