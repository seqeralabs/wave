/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DigestStoreEncodeStrategyTest extends Specification{

    def 'should encode and decode zipped digest store' () {
        given:
        def DATA = 'Hello wold!'
        def encoder = DigestStoreEncodeStrategy.create()
        and:
        def data = ZippedDigestStore.fromUncompressed(DATA.bytes, 'my/media', '12345', 2000)

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.bytes == data.bytes
        copy.digest == data.digest
        copy.mediaType == data.mediaType
        copy.size == data.size
        and:
        new String(copy.bytes) == DATA
    }

    def 'should encode and decode http digest store' () {
        given:
        def encoder = DigestStoreEncodeStrategy.create()
        and:
        def data = new HttpDigestStore(
                'http://foo.com/this/that',
                'text/json',
                '12345',
                2000 )

        when:
        def json = encoder.encode(data)

        and:
        def copy = (HttpDigestStore) encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.location == data.location
        copy.digest == data.digest
        copy.mediaType == data.mediaType
        copy.size == data.size
    }
}
