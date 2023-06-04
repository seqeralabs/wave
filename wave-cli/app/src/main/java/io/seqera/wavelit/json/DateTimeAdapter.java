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

package io.seqera.wavelit.json;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
/**
 * Date time adapter for Moshi JSON serialisation
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DateTimeAdapter {

    @ToJson
    public String serializeInstant(Instant value) {
        return value!=null ? DateTimeFormatter.ISO_INSTANT.format(value) : null;
    }

    @FromJson
    public Instant deserializeInstant(String value) {
        return value!=null ? Instant.from(DateTimeFormatter.ISO_INSTANT.parse(value)) : null;
    }

    @ToJson
    public String serializeDuration(Duration value) {
        return value != null ? String.valueOf(value.toNanos()) : null;
    }

    @FromJson
    public Duration deserializeDuration(String value) {
        if( value==null )
            return null;
        // for backward compatibility duration may be encoded as float value
        // instead of long (number of nanoseconds) as expected
        final Long val0 = value.contains(".") ? Math.round(Double.valueOf(value) * 1_000_000_000) : Long.valueOf(value);
        return Duration.ofNanos(val0);
    }
}
