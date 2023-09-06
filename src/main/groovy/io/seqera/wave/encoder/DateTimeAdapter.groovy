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
    Duration deserializeDuration(String value) {
        if( value==null )
            return null
        // for backward compatibility duration may be encoded as float value
        // instead of long (number of nanoseconds) as expected
        final val0 = value.contains('.') ? Math.round(value.toDouble() * 1_000_000_000) : value.toLong()
        return value != null ? Duration.ofNanos(val0) : null
    }
}
