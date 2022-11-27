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
