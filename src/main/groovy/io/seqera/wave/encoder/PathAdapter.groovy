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
