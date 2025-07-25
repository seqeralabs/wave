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
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 * Model Job manager configuration settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingBeans = WaveLite)
@ToString(includeNames = true, includePackage = false)
@Singleton
class JobManagerConfig {

    @Value('${wave.job-manager.grace-interval:30s}')
    Duration graceInterval

    @Value('${wave.job-manager.poll-interval:1s}')
    Duration pollInterval

    @Value('${wave.job-manager.scheduler-interval:1s}')
    Duration schedulerInterval

    @Value('${wave.job-manager.scheduler-max-delay:1m}')
    Duration schedulerMaxDelay

    @Value('${wave.job-manager.max-running-jobs:20}')
    int maxRunningJobs
}
