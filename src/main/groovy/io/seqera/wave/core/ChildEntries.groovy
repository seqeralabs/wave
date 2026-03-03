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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * A list of per-platform child IDs (builds or scans).
 *
 * Serialises to/from JSON as: [{"id":"sc-abc_1","platform":"linux/amd64"}, ...]
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ChildEntries extends ArrayList<ChildEntries.Entry> {

    @CompileStatic
    @EqualsAndHashCode
    static class Entry {
        String id
        String platform

        Entry() {}

        Entry(String id, String platform) {
            this.id = id
            this.platform = platform
        }
    }

    ChildEntries() {
        super()
    }

    ChildEntries(List<Entry> entries) {
        super(entries ?: Collections.<Entry>emptyList())
    }

    /**
     * Create from a map of id-by-platform.
     * @param idByPlatform Map where keys are IDs and values are platform strings
     * @return A new ChildEntries, or null if the map is null/empty
     */
    static ChildEntries of(Map<String, String> idByPlatform) {
        if( !idByPlatform )
            return null
        final result = new ChildEntries()
        for( Map.Entry<String,String> it : idByPlatform.entrySet() ) {
            result.add(new Entry(it.key, it.value))
        }
        return result
    }

    /**
     * Get the first/primary ID
     */
    String primary() {
        return this ? this[0].id : null
    }

    /**
     * Get all IDs
     */
    List<String> allIds() {
        return this.collect { it.id }
    }

    // -- template binding helpers --

    static void populateScanBinding(Map binding, String scanId, ChildEntries scanChildIds, boolean succeeded, String serverUrl) {
        if( scanChildIds && succeeded ) {
            binding.scan_entries = scanChildIds.collect { Entry entry ->
                [scan_id: entry.id, scan_platform: entry.platform, scan_url: "${serverUrl}/view/scans/${entry.id}"] as Map<String,String>
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

    static void populateBuildBinding(Map binding, ChildEntries buildChildIds, String serverUrl) {
        if( buildChildIds ) {
            binding.build_entries = buildChildIds.collect { Entry entry ->
                [build_id: entry.id, build_platform: entry.platform, build_url: "${serverUrl}/view/builds/${entry.id}"] as Map<String,String>
            }
        }
        else {
            binding.build_entries = null
        }
    }

}
