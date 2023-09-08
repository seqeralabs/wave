/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
