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

package io.seqera.wave.service.scan

import groovy.transform.CompileStatic

/**
 * Helper class for encoding/decoding multi-platform scan IDs.
 *
 * Single-platform scanId: "sc-abc_1"
 * Multi-platform scanId: "sc-abc_1:linux/amd64,sc-def_2:linux/arm64"
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ScanIds {

    /**
     * Check if the scanId represents multiple scans
     */
    static boolean isMulti(String value) {
        return value != null && value.contains(':')
    }

    /**
     * Encode a map of scanId-by-platform into a single string.
     * @param scanIdByPlatform Map where keys are scanIds and values are platform strings (e.g. "linux/amd64")
     * @return Encoded string e.g. "sc-abc_1:linux/amd64,sc-def_2:linux/arm64"
     */
    static String encode(Map<String, String> scanIdByPlatform) {
        if( !scanIdByPlatform )
            return null
        if( scanIdByPlatform.size() == 1 )
            return scanIdByPlatform.keySet().first()
        return scanIdByPlatform
                .collect { scanId, platform -> "${scanId}:${platform}" }
                .join(',')
    }

    /**
     * Decode a scanId string into a list of (scanId, platform) pairs.
     * For single-platform scanIds, returns a single entry with null platform.
     */
    static List<Map.Entry<String, String>> decode(String value) {
        if( !value )
            return Collections.<Map.Entry<String, String>>emptyList()
        if( !isMulti(value) ) {
            final Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(value, null)
            return Collections.<Map.Entry<String, String>>singletonList(entry)
        }
        final List<Map.Entry<String, String>> result = new ArrayList<>()
        for( String part : value.tokenize(',') ) {
            final idx = part.indexOf(':')
            final scanId = part.substring(0, idx)
            final platform = part.substring(idx + 1)
            result.add(new AbstractMap.SimpleEntry<String, String>(scanId, platform))
        }
        return result
    }

    /**
     * Get the first/primary scanId (works for both single and multi)
     */
    static String primary(String value) {
        if( !value )
            return null
        if( !isMulti(value) )
            return value
        final idx = value.indexOf(':')
        return value.substring(0, idx)
    }

    /**
     * Get all scanIds from the encoded string
     */
    static List<String> allIds(String value) {
        return decode(value).collect { it.key }
    }

    /**
     * Populate scan-related binding keys for view templates and emails.
     * Handles both single and multi-platform scan IDs.
     *
     * Sets: scan_entries (list of maps), scan_url, scan_id
     *
     * @param binding The binding map to populate
     * @param scanId The raw scanId (single or multi-platform encoded)
     * @param succeeded Whether the associated build/mirror succeeded
     * @param serverUrl The server base URL
     */
    static void populateScanBinding(Map binding, String scanId, boolean succeeded, String serverUrl) {
        if( scanId && succeeded && isMulti(scanId) ) {
            binding.scan_entries = decode(scanId).collect { Map.Entry<String, String> entry ->
                [scan_id: entry.key, scan_platform: entry.value, scan_url: "${serverUrl}/view/scans/${entry.key}"] as Map<String,String>
            }
            binding.scan_url = null
            binding.scan_id = primary(scanId)
        }
        else {
            binding.scan_entries = null
            binding.scan_url = scanId && succeeded ? "${serverUrl}/view/scans/${scanId}" : null
            binding.scan_id = scanId
        }
    }

}
