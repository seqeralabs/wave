/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.storage.reader

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DataContentReaderTest extends Specification {

    def 'should decode data' () {
        given:
        def encoded = 'Hello world'.bytes.encodeBase64().toString()
        def reader = new DataContentReader(encoded)

        when:
        def decoded = reader.readAllBytes()
        then:
        new String(decoded) == 'Hello world'
    }

    def 'should decode data string' () {
        given:
        def encoded = 'Hello world'.bytes.encodeBase64().toString()
        def reader = ContentReaderFactory.of("data:$encoded")

        when:
        def decoded = reader.readAllBytes()
        then:
        new String(decoded) == 'Hello world'
    }
}
