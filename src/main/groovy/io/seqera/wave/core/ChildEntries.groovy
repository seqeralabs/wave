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

package io.seqera.wave.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.CompileStatic

/**
 * Value type representing a set of per-platform child IDs (builds or scans).
 *
 * Serialises to/from an encoded string:
 *   "id1:platform1,id2:platform2"
 *
 * Example: "sc-abc_1:linux/amd64,sc-def_2:linux/arm64"
 *
 * Used as the type for both {@code buildChildIds} and {@code scanChildIds}.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ChildEntries {

    /**
     * The encoded string representation
     */
    private final String encoded

    @JsonCreator
    ChildEntries(String encoded) {
        this.encoded = encoded
    }

    /**
     * Create from a map of id-by-platform.
     * @param idByPlatform Map where keys are IDs and values are platform strings (e.g. "linux/amd64")
     * @return A new ChildEntries, or null if the map is null/empty
     */
    static ChildEntries of(Map<String, String> idByPlatform) {
        if( !idByPlatform )
            return null
        if( idByPlatform.size() == 1 ) {
            final entry = idByPlatform.entrySet().first()
            return new ChildEntries("${entry.key}:${entry.value}")
        }
        final encoded = idByPlatform
                .collect { id, platform -> "${id}:${platform}" }
                .join(',')
        return new ChildEntries(encoded)
    }

    /**
     * Decode into a list of (id, platform) pairs.
     */
    List<Map.Entry<String, String>> decode() {
        if( !encoded )
            return Collections.<Map.Entry<String, String>>emptyList()
        final List<Map.Entry<String, String>> result = new ArrayList<>()
        for( String part : encoded.tokenize(',') ) {
            final idx = part.indexOf(':')
            if( idx < 0 ) {
                result.add(new AbstractMap.SimpleEntry<String, String>(part, null))
            }
            else {
                final id = part.substring(0, idx)
                final platform = part.substring(idx + 1)
                result.add(new AbstractMap.SimpleEntry<String, String>(id, platform))
            }
        }
        return result
    }

    /**
     * Get the first/primary ID
     */
    String primary() {
        if( !encoded )
            return null
        final idx = encoded.indexOf(':')
        if( idx < 0 )
            return encoded
        return encoded.substring(0, idx)
    }

    /**
     * Get all IDs
     */
    List<String> allIds() {
        return decode().collect { it.key }
    }

    /**
     * Jackson serialisation: serialise as the encoded string
     */
    @JsonValue
    String toString() {
        return encoded
    }

    /**
     * Groovy truth: non-null and non-empty encoded string
     */
    boolean asBoolean() {
        return encoded != null && !encoded.isEmpty()
    }

    @Override
    boolean equals(Object o) {
        if( this.is(o) ) return true
        if( o == null || getClass() != o.getClass() ) return false
        return encoded == ((ChildEntries) o).encoded
    }

    @Override
    int hashCode() {
        return encoded != null ? encoded.hashCode() : 0
    }

    // -- template binding helpers --

    /**
     * Populate scan-related binding keys for view templates and emails.
     * Handles both single-platform (scanId) and multi-platform (scanChildIds) scan IDs.
     *
     * Sets: scan_entries (list of maps), scan_url, scan_id
     */
    static void populateScanBinding(Map binding, String scanId, ChildEntries scanChildIds, boolean succeeded, String serverUrl) {
        if( scanChildIds && succeeded ) {
            binding.scan_entries = scanChildIds.decode().collect { Map.Entry<String, String> entry ->
                [scan_id: entry.key, scan_platform: entry.value, scan_url: "${serverUrl}/view/scans/${entry.key}"] as Map<String,String>
            }
            binding.scan_url = null
            binding.scan_id = scanChildIds.primary()
        }
        else {
            binding.scan_entries = null
            binding.scan_url = scanId && succeeded ? "${serverUrl}/view/scans/${scanId}" : null
            binding.scan_id = scanId
        }
    }

    /**
     * Populate build-related binding keys for child (per-arch) build entries.
     *
     * Sets: build_entries (list of maps with build_id, build_platform, build_url)
     */
    static void populateBuildBinding(Map binding, ChildEntries buildChildIds, String serverUrl) {
        if( buildChildIds ) {
            binding.build_entries = buildChildIds.decode().collect { Map.Entry<String, String> entry ->
                [build_id: entry.key, build_platform: entry.value, build_url: "${serverUrl}/view/builds/${entry.key}"] as Map<String,String>
            }
        }
        else {
            binding.build_entries = null
        }
    }

}
