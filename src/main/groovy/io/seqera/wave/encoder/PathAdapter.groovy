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

import java.nio.file.Path

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Mosh adapter for {@link Path}. Only support default file system provider
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PathAdapter {

    @ToJson
    String serialize(Path path) {
        return path != null ? path.toString() : null
    }

    @FromJson
    Path deserialize(String data) {
        return data != null ? Path.of(data) : null
    }

}
