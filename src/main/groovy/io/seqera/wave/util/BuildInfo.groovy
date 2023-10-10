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

package io.seqera.wave.util

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Registry build info helper
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class BuildInfo {

    private static Properties properties

    static {
        final BUILD_INFO = '/META-INF/build-info.properties'
        properties = new Properties()
        try {
            properties.load( BuildInfo.getResourceAsStream(BUILD_INFO) )
        }
        catch( Exception e ) {
            log.warn "Unable to parse $BUILD_INFO - Cause ${e.message ?: e}"
        }
    }

    static Properties getProperties() { properties }

    static String getVersion() { properties.getProperty('version') }

    static String getCommitId() { properties.getProperty('commitId')}

    static String getName() { properties.getProperty('name') }

    static String getFullVersion() {
        "${version}_${commitId}"
    }

}
