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
