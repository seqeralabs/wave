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

package io.seqera.wave.encoder

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import groovy.transform.CompileStatic

/**
 * Moshi adapter for JSON serialization
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ByteArrayAdapter {

    @ToJson
    String serialize(byte[] data) {
        return Base64.getEncoder().encodeToString(data)
    }

    @FromJson
    byte[] deserialize(String data) {
        return Base64.getDecoder().decode(data)
    }
}
