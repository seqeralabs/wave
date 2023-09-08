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
