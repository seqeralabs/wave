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
    List<String> layers

    ManifestSpec() {
        this(Map.of())
    }

    ManifestSpec(Map opts) {
        this.architecture = opts.architecture
        this.container = opts.container
        this.config = new ConfigSpec( opts.config as Map ?: Map.of() )
        this.created = opts.created ? Instant.parse(opts.created as String) : null
        this.layers = parseLayers(opts.rootfs as Map ?: Map.of())
    }

    static ManifestSpec parse(String manifest) {
        final payload = new JsonSlurper().parseText(manifest) as Map
        return new ManifestSpec(payload)
    }

    static ManifestSpec parseV1(String manifest) {
        // parse the content
        final opts = new JsonSlurper().parseText(manifest) as Map

        // fetch layers
        final layers = (opts.fsLayers as List<Map>)
                ?.reverse()
                ?.collect(it-> it.blobSum)
        // fetch the history from the v1 manifest
        final history = opts.history as List<Map<String,String>>
        if( history ) {
            final configJson = history.get(0).v1Compatibility
            final configObj = new JsonSlurper().parseText(configJson) as Map
            // add layers
            configObj.put('rootfs', [diff_ids: layers])
            return new ManifestSpec( configObj )
        }
        throw new IllegalArgumentException("Invalid Docker v1 manifest")
    }

    static List<String> parseLayers(Map<String,List> rootfs) {
        final ids = rootfs?.diff_ids
        if( !ids )
            return List.<String>of()
        final result = new ArrayList(10)
        for( String it : ids )
            result.add(it)
        return result
    }
}
