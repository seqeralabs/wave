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

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.context.annotation.Value
import io.seqera.util.time.DurationUtils
import jakarta.inject.Singleton
/**
 * Configuration to be used by {@link io.seqera.wave.service.request.ContainerRequestService}
 *
 * @author: Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ToString(includePackage = false, includeNames = true)
@Singleton
class ContainerRequestConfig {

    /**
     * The default duration of a container request time-to-live, i.e. how long
     * a container token is valid and therefore an ephemeral container can be accessed.
     */
    @Value('${wave.tokens.cache.duration:36h}')
    Duration cacheDuration

    /**
     * The maximum duration a container request time-to-live can be extended to, measured from the
     * container request creation time. Hard ceiling on how long an ephemeral token can ever live.
     */
    @Value('${wave.tokens.cache.max-duration:2d}')
    Duration cacheMaxDuration

    /**
     * The short time-to-live granted to a workflow-bound token on each renewal. The token lapses
     * this long after the last successful renewal, which bounds how long it stays valid after the
     * workflow completes.
     */
    @Value('${wave.tokens.cache.access-ttl:15m}')
    Duration accessTtl

    /**
     * How often a workflow-bound token is re-checked and renewed while its workflow is active.
     * Must be shorter than {@link #accessTtl} so several renewals fit inside a token's lifetime.
     */
    @Value('${wave.tokens.cache.refresh-interval:270s}')
    Duration refreshInterval

    /**
     * The delay between two consecutive watcher runs.
     */
    @Value('${wave.tokens.watcher.interval:10s}')
    Duration watcherInterval

    /**
     * The delay after which the watcher service is launched on bootstrap.
     */
    @Value('${wave.tokens.watcher.delay:5s}')
    Duration watcherDelay

    /**
     * The number of container requests processed in a watcher cycle.
     */
    @Value('${wave.tokens.watcher.count:250}')
    int watcherCount

    Duration getWatcherDelayRandomized() {
        DurationUtils.randomDuration(watcherDelay, 0.4f)
    }

}
