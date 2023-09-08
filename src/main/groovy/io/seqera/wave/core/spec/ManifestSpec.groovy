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

package io.seqera.wave.core.spec

import java.time.Instant

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Model a container manifest specification
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ManifestSpec {

    String architecture
    ConfigSpec config
    String container
    Instant created

    ManifestSpec() {
        this(Map.of())
    }

    ManifestSpec(Map opts) {
        this.architecture = opts.architecture
        this.container = opts.container
        this.config = new ConfigSpec( opts.config as Map ?: Map.of() )
        this.created = opts.created ? Instant.parse(opts.created as String) : null
    }

    static ManifestSpec parse(String manifest) {
        final payload = new JsonSlurper().parseText(manifest) as Map
        return new ManifestSpec(payload)
    }

    static ManifestSpec parseV1(String manifest) {
        // parse the content
        final opts = new JsonSlurper().parseText(manifest) as Map

        // fetch the history from the v1 manifest
        final history = opts.history as List<Map<String,String>>
        if( history ) {
            final configJson = history.get(0).v1Compatibility
            final configObj = new JsonSlurper().parseText(configJson) as Map
            return new ManifestSpec( configObj )
        }
        throw new IllegalArgumentException("Invalid Docker v1 manifest")
    }
}
