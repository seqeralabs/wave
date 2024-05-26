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

package io.seqera.wave.tower.auth

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
@ToString(includeNames = true, includePackage = false)
class JwtConfig {

    @Value('${tower.jwt.refresh.interval:30s}')
    Duration refreshInterval

    @Value('${tower.jwt.heartbeat.interval:5s}')
    Duration heartbeatInterval

    @Value('${tower.jwt.heartbeat.delay:5s}')
    Duration heartbeatDelay

    @Value('${tower.jwt.heartbeat.count:10}')
    int heartbeatCount

}
