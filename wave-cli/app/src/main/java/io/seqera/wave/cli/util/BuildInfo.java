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

package io.seqera.wave.cli.util;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class BuildInfo {

    private static final Logger log = LoggerFactory.getLogger(BuildInfo.class);

    private static Properties properties;

    static {
        final String BUILD_INFO = "/META-INF/build-info.properties";
        properties = new Properties();
        try {
            properties.load( BuildInfo.class.getResourceAsStream(BUILD_INFO) );
        }
        catch( Exception e ) {
            log.warn("Unable to parse $BUILD_INFO - Cause: " + e.getMessage());
        }
    }

    static Properties getProperties() { return properties; }

    static public String getVersion() { return properties.getProperty("version"); }

    static public String getCommitId() { return properties.getProperty("commitId"); }

    static public String getName() { return properties.getProperty("name"); }

    static public String getFullVersion() {
        return getVersion() + "_" + getCommitId();
    }

}
