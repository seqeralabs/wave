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
