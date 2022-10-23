package io.seqera.wave.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utils to handle date-time objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DataTimeUtils {

    static public String offsetId(String timestamp) {
        return timestamp!=null
                ? OffsetDateTime.parse(timestamp).getOffset().getId()
                : null;
    }

    static public String formatTimestamp(Instant ts, String zoneId) {
        if( ts==null )
            return null;
        if( zoneId==null )
            zoneId = OffsetDateTime.now().getOffset().getId();
        return  DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm (O)")
                .withZone(ZoneId.of(zoneId))
                .format(ts);

    }

}
