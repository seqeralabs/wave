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

package io.seqera.wave.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Implements a Logback logger filter that switch off all
 * log event lower than a given threshold
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class LoggerLevelFilter extends AbstractMatcherFilter<ILoggingEvent> {

    Level level;

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        if (event.getLevel().toInt() >= level.toInt() ) {
            return onMatch;
        } else {
            return onMismatch;
        }
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void start() {
        if (this.level != null) {
            super.start();
        }
    }
}
