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
