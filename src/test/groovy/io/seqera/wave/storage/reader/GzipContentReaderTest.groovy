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

package io.seqera.wave.storage.reader

import spock.lang.Specification

import io.seqera.wave.util.ZipUtils

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GzipContentReaderTest extends Specification {

    def 'should decode data' () {
        given:
        def DATA = 'Hola mundo!'

        when:
        def reader1 = GzipContentReader.fromPlainString(DATA)
        then:
        new String(reader1.readAllBytes()) == DATA

        when:
        final compressed = ZipUtils.compress(DATA);
        and:
        def reader2 = GzipContentReader.fromBase64EncodedString(compressed.encodeBase64().toString())
        then:
        new String(reader2.readAllBytes()) == DATA
    }
}
