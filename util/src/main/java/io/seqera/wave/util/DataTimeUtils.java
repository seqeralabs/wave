/*
 * Copyright 2025, Seqera Labs
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

package io.seqera.wave.util;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utils to handle date-time objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DataTimeUtils {

    private static final Logger log = LoggerFactory.getLogger(DataTimeUtils.class);

    static public String offsetId(String timestamp) {
        return timestamp!=null
                ? OffsetDateTime.parse(timestamp).getOffset().getId()
                : null;
    }

    static public String formatTimestamp(Instant ts, String zoneId) {
        if( ts==null )
            return null;
        if( zoneId==null )
            zoneId = ZoneId.of("GMT").getId();
        return  DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm (O)")
                .withZone(ZoneId.of(zoneId))
                .format(ts);
    }

    static public String formatTimestamp(Instant ts) {
        return formatTimestamp(ts,null);
    }

    static public String formatTimestamp(OffsetDateTime ts) {
        if( ts==null )
            return null;
        return formatTimestamp(ts.toInstant(), ts.getOffset().getId());
    }

    static public String formatDuration(Duration duration) {
        if( duration==null )
            return null;
        final long time = duration.toMillis();
        int minutes = (int) time / (60 * 1_000) ;
        int seconds = (int) (time / 1_000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    static public OffsetDateTime parseOffsetDateTime(String ts) {
        try {
            return ts != null ? OffsetDateTime.parse(ts) : null;
        }
        catch (DateTimeParseException e) {
            log.warn ("Unable to parse timestamp {} - cause: {}", ts, e.getMessage());
            return null;
        }
    }
}
