/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class JacksonHelperTest extends Specification {

    def "toJsonAndAppend should append map to config and return JSON string"() {
        given:
        def config = [name: "test"]
        Map map = [id: "token"]

        when:
        def result = JacksonHelper.toJsonAndAppend(config, map)

        then:
        result == '{"name":"test","id":"token"}'
    }
}
