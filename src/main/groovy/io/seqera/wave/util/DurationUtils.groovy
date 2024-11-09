/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.util

import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

import groovy.transform.CompileStatic
/**
 * Utility functions for handling duration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class DurationUtils {

    static Duration randomDuration(Duration min, Duration max) {
        if (min > max) {
            throw new IllegalArgumentException("Min duration must be less than or equal to max duration")
        }

        long minNanos = min.toNanos()
        long maxNanos = max.toNanos()
        long randomNanos = ThreadLocalRandom.current().nextLong(minNanos, maxNanos + 1)

        return Duration.ofNanos(randomNanos)
    }

    static Duration randomDuration(Duration reference, float intervalPercentage) {
        if (intervalPercentage < 0 || intervalPercentage > 1) {
            throw new IllegalArgumentException("Interval percentage must be between 0 and 1")
        }

        long refNanos = reference.toNanos()
        long intervalNanos = (long)(refNanos * intervalPercentage)

        long minNanos = Math.max(0, refNanos - intervalNanos)
        long maxNanos = refNanos + intervalNanos

        long randomNanos = ThreadLocalRandom.current().nextLong(minNanos, maxNanos + 1)

        return Duration.ofNanos(randomNanos)
    }

}
