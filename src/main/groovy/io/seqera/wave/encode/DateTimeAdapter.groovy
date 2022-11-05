package io.seqera.wave.encode

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import groovy.transform.CompileStatic

/**
 * Date time adapter for Moshi JSON serialisation
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class DateTimeAdapter {

    @ToJson
    String serializeInstant(Instant value) {
        return value ? DateTimeFormatter.ISO_INSTANT.format(value) : null
    }

    @FromJson
    Instant deserializeInstant(String value) {
        return value ? Instant.from(DateTimeFormatter.ISO_INSTANT.parse(value)) : null
    }

    @ToJson
    String serializeDuration(Duration value) {
        return value != null ? value.toNanos() : null
    }

    @FromJson
    Duration deserializeDuration(Long value) {
        return value != null ? Duration.ofNanos(value) : null
    }
}
