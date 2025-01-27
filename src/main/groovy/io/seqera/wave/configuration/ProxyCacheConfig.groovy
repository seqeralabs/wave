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
import jakarta.inject.Singleton

/**
 * Model {@link io.seqera.wave.proxy.ProxyCache} configuration settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
@ToString(includeNames = true, includePackage = false)
class ProxyCacheConfig {

    @Value('${wave.proxy-cache.duration:120s}')
    private Duration duration

    @Value('${wave.proxy-cache.max-size:10000}')
    private int maxSize

    @Value('${wave.proxy-cache.enabled:true}')
    private boolean enabled

    Duration getDuration() {
        return duration
    }

    int getMaxSize() {
        return maxSize
    }

    boolean getEnabled() {
        return enabled
    }
}
