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
import io.micronaut.core.version.VersionUtils;

/**
 * Helper class to find out runtime info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RuntimeInfo {

    static String getOsName() { System.getProperty('os.name') }
    static String getOsVersion() { System.getProperty('os.version') }
    static String getGroovyVersion() { GroovySystem.getVersion() }
    static String getJvmName() { System.getProperty('java.vm.name') }
    static String getJvmVersion() { System.getProperty('java.runtime.version') }
    static String getFileEncoding() { System.getProperty('file.encoding') }
    static String getFileNameEncoding() { System.getProperty('sun.jnu.encoding') }
    static String getMicronautVersion() { VersionUtils.getMicronautVersion() }

    static String info(String newline='\n') {
        final result = new StringBuilder()
        result << "System: ${osName} ${osVersion}" << newline
        result << "Micronaut: ${getMicronautVersion()}" << newline
        result << "Runtime: Groovy ${groovyVersion} on ${jvmName} ${jvmVersion}" << newline
        result << "Encoding: ${fileEncoding} (${fileNameEncoding})" << newline
        return result.toString()
    }

}
