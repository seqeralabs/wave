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

import groovy.transform.CompileStatic
import org.apache.commons.lang3.SerializationUtils

/**
 * Helper class to encode/decode {@link DigestStore} objects
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Deprecated
class DigestStoreEncoder {

    static String encode(DigestStore store) {
        return SerializationUtils.serialize(store).encodeBase64().toString()
    }

    static DigestStore decode(String encoded) {
        return SerializationUtils.deserialize(encoded.decodeBase64())
    }

}
