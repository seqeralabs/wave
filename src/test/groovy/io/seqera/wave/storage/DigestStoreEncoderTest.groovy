/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.storage

import spock.lang.Specification

import io.seqera.wave.storage.reader.DataContentReader
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Deprecated
class DigestStoreEncoderTest extends Specification {

    def 'should encode and decode digest store classes' () {
        given:
        def CONTENT = 'Hello world!'
        and:
        def store = new ZippedDigestStore(CONTENT.bytes, 'text', 'sha256:122345567890', 1000)

        when:
        def encoded = DigestStoreEncoder.encode(store)
        and:
        def decoded = DigestStoreEncoder.decode(encoded)

        then:
        decoded.bytes == CONTENT.bytes
        decoded.digest == 'sha256:122345567890'
        decoded.mediaType == 'text'
        decoded.size == 1000
    }

    def 'should encode and decode lazy digest store' () {
        given:
        def CONTENT = 'Hello world!'
        def data = new DataContentReader(CONTENT.bytes.encodeBase64().toString())

        and:
        def store = new LazyDigestStore(data, 'text', 'sha256:122345567890', 2000)

        when:
        def encoded = DigestStoreEncoder.encode(store)
        and:
        def decode = DigestStoreEncoder.decode(encoded)

        then:
        decode.bytes == CONTENT.bytes
        decode.digest == 'sha256:122345567890'
        decode.mediaType == 'text'
        decode.size == 2000

    }
}
