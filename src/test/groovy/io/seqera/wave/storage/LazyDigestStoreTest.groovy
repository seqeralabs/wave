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

package io.seqera.wave.storage

import spock.lang.Specification

import io.seqera.wave.storage.reader.DataContentReader
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LazyDigestStoreTest extends Specification {

    def 'should load a lazy digest' () {
        given:
        def CONTENT = 'Hello world!'
        and:
        def data = new DataContentReader(CONTENT.bytes.encodeBase64().toString())

        when:
        def digest = new LazyDigestStore(data, 'text', 'sha256:122345567890', 1000)
        then:
        digest.bytes == CONTENT.bytes
        digest.digest == 'sha256:122345567890'
        digest.mediaType == 'text'
        digest.size == 1000
    }

}
