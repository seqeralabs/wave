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
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
/**
 * Model mirror service config options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class MirrorConfig {

    @Value('${wave.mirror.max-duration:15m}')
    Duration maxDuration

    @Value('${wave.mirror.status.delay:4s}')
    Duration statusDelay

    @Value('${wave.mirror.status.duration:1h}')
    Duration statusDuration

    @Value('${wave.mirror.failure.duration:50s}')
    Duration failureDuration

    @Value('${wave.mirror.skopeoImage:`quay.io/skopeo/stable`}')
    String skopeoImage

    @Value('${wave.mirror.retry-attempts:3}')
    Integer retryAttempts

    @Nullable
    @Value('${wave.mirror.requestsCpu}')
    String requestsCpu

    @Nullable
    @Value('${wave.mirror.requestsMemory}')
    String requestsMemory

}
