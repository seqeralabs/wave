/*
 * Copyright 2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.cli.json;

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
