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
 * Serialises to/from JSON via Moshi as: {"refs":[{"id":"sc-abc_1","value":"linux/amd64"}, ...]}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@EqualsAndHashCode(includes = 'refs')
class ChildRefs implements Iterable<Ref> {

    @CompileStatic
    @EqualsAndHashCode
    static class Ref {
        String id
        String value

        Ref() {}

        Ref(String id, String value) {
            this.id = id
            this.value = value
        }

        @Override
        String toString() {
            return "${id}:${value}"
        }
    }

    List<Ref> refs

    ChildRefs() {
        this.refs = []
    }

    ChildRefs(List<Ref> refs) {
        this.refs = refs != null ? new ArrayList<>(refs) : []
    }

    /**
     * Create from a map of id-by-platform.
     * @param idByPlatform Map where keys are IDs and values are platform strings
     * @return A new ChildRefs, or null if the map is null/empty
     */
    static ChildRefs of(Map<String, String> idByPlatform) {
        if( !idByPlatform )
            return null
        final result = new ChildRefs()
        for( Map.Entry<String,String> it : idByPlatform.entrySet() ) {
            result.add(new Ref(it.key, it.value))
        }
        return result
    }

    // -- delegate methods --

    int size() { refs.size() }

    boolean isEmpty() { refs.isEmpty() }

    Ref getAt(int index) { refs[index] }

    void add(Ref entry) { refs.add(entry) }

    boolean asBoolean() { refs != null && !refs.isEmpty() }

    @Override
    Iterator<Ref> iterator() { refs.iterator() }

    def <T> List<T> collect(Closure<T> closure) { refs.collect(closure) }

    /**
     * Get the first/primary ID
     */
    String primary() {
        return refs ? refs[0].id : null
    }

    /**
     * Get all IDs
     */
    List<String> allIds() {
        return refs.collect { it.id }
    }

    @Override
    String toString() {
        return refs.collect { it.toString() }.toString()
    }

    // -- template binding helpers --

    static void populateScanBinding(Map binding, String scanId, ChildRefs scanChildIds, boolean succeeded, String serverUrl) {
        if( scanChildIds && succeeded ) {
            binding.scan_entries = scanChildIds.collect { Ref entry ->
                [scan_id: entry.id, scan_platform: entry.value, scan_url: "${serverUrl}/view/scans/${entry.id}"] as Map<String,String>
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

    static void populateBuildBinding(Map binding, ChildRefs buildChildIds, String serverUrl) {
        if( buildChildIds ) {
            binding.build_entries = buildChildIds.collect { Ref entry ->
                [build_id: entry.id, build_platform: entry.value, build_url: "${serverUrl}/view/builds/${entry.id}"] as Map<String,String>
            }
        }
        else {
            binding.build_entries = null
        }
    }

}
