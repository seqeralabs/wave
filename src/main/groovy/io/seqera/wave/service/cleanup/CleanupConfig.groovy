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

package io.seqera.wave.service.cleanup

import java.time.Duration
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 * Model configuration settings for resources cleanup
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@CompileStatic
@Singleton
class CleanupConfig {

    @Value('${wave.cleanup.strategy}')
    @Nullable
    String strategy

    @Value('${wave.cleanup.succeeded:5m}')
    Duration succeededDuration

    @Value('${wave.cleanup.failed:1d}')
    Duration failedDuration

    @Value('${wave.cleanup.range:200}')
    int cleanupRange

    @Value('${wave.cleanup.startup-delay:10s}')
    Duration cleanupStartupDelay

    @Value('${wave.cleanup.run-interval:30s}')
    Duration cleanupRunInterval

}
