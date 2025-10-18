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

package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.ToString
import io.micronaut.context.annotation.Value
import io.seqera.wave.util.DurationUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Configuration to be used by {@link io.seqera.wave.service.request.ContainerRequestService}
 *
 * @author: Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 *
 */
@ToString(includePackage = false, includeNames = true)
@Singleton
class ContainerRequestConfig {

    @Inject
    Cache cache

    @Inject
    Watcher watcher

    /**
     * Model container token caching configuration settings
     */
    @ToString(includePackage = false, includeNames = true)
    @Singleton
    static class Cache {

        /**
         * The default duration of a container request time-to-live.
         * This determines how long a container token is valid, and
         * therefore an ephemeral container can be accessed.
         */
        @Value('${wave.tokens.cache.duration:3h}')
        Duration duration

        /**
         * The maximum duration of a container request time-to-live.
         * This determines how long a container token is valid, and
         * therefore an ephemeral container can be accessed.
         */
        @Value('${wave.tokens.cache.max-duration:2d}')
        Duration maxDuration

        /**
         * This method returns the period of time between two consecutive check events.
         * The interval determines how frequently a refresh operation is triggered.
         * A shorter interval means more frequent checks, while a longer interval reduces checks frequency.
         */
        @Value('${wave.tokens.cache.check-interval:30h}')
        Duration checkInterval

    }

    /**
     * Model container request watcher configuration settings
     */
    @ToString(includePackage = false, includeNames = true)
    static class Watcher {

        /**
         * Determine the delay after which the container request watcher service is run
         */
        @Value('${wave.tokens.watcher.interval:10s}')
        Duration interval

        /**
         * Determine the delay after which the watcher service is launched after the bootstrap
         */
        @Value('${wave.tokens.watcher.delay:5s}')
        Duration delay

        /**
         * Determine the number of container requests that are processed in watcher cycle
         */
        @Value('${wave.tokens.watcher.count:250}')
        int count

        Duration getDelayRandomized() {
            DurationUtils.randomDuration(getDelay(), 0.4f)
        }

    }

}
