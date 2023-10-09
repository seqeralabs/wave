/*
 * Copyright 2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
