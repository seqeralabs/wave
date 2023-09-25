/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
