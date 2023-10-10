/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import io.micronaut.core.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.builder.BuildResult
import jakarta.inject.Singleton

/**
 * Implement a cleanup strategy
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
@Slf4j
class CleanupStrategy {

    @Value('${wave.build.cleanup}')
    @Nullable
    String cleanup

    @Value('${wave.debug:false}')
    Boolean debugMode


    boolean shouldCleanup(BuildResult result) {
        shouldCleanup(result?.exitStatus)
    }

    boolean shouldCleanup(Integer exitStatus) {
        if( cleanup==null )
            return !debugMode
        if( cleanup == 'true' )
            return true
        if( cleanup == 'false' )
            return false
        if( cleanup.toLowerCase() == 'onsuccess' ) {
            return exitStatus==0
        }
        log.debug "Invalid cleanup value: '$cleanup'"
        return true
    }

}
