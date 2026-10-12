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

package io.seqera.wave.util

import java.lang.management.ManagementFactory

import com.sun.management.OperatingSystemMXBean
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.version.VersionUtils;

/**
 * Helper class to find out runtime info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
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
        final os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()
        result << "Process: ${ManagementFactory.getRuntimeMXBean().getName()}" << newline
        result << "CPUs: ${Runtime.runtime.availableProcessors()} - Mem: ${totMem(os)} (free: ${freeMem(os)}) - Swap: ${totSwap(os)} (free: ${freeSwap(os)})" << newline

        return result.toString()
    }

    static private String totMem(OperatingSystemMXBean os) {
        try {
            return formatBytes(os.totalMemorySize)
        }
        catch (Throwable t) {
            log.debug "Unable to fetch totalMemorySize - ${t.message ?: t}"
            return '-'
        }
    }

    static private String freeMem(OperatingSystemMXBean os) {
        try {
            return formatBytes(os.freeMemorySize)
        }
        catch (Throwable t) {
            log.debug "Unable to fetch freeMemorySize - ${t.message ?: t}"
            return '-'
        }
    }

    static private String totSwap(OperatingSystemMXBean os) {
        try {
            formatBytes(os.totalSwapSpaceSize)
        }
        catch (Throwable t) {
            log.debug "Unable to fetch totalSwapSpaceSize - ${t.message ?: t}"
            return '-'
        }
    }

    static private String freeSwap(OperatingSystemMXBean os) {
        try {
            return formatBytes(os.freeSwapSpaceSize)
        }
        catch (Throwable t) {
            log.debug "Unable to fetch freeSwapSpaceSize - ${t.message ?: t}"
            return '-'
        }
    }

    static private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String units = "KMGTPE"; // Kilobyte, Megabyte, Gigabyte, etc.
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), units.charAt(exp - 1));
    }
}
