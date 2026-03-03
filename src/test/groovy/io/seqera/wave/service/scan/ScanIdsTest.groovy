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

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for {@link ScanIds}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanIdsTest extends Specification {

    @Unroll
    def 'should detect multi scanId: #VALUE'() {
        expect:
        ScanIds.isMulti(VALUE) == EXPECTED

        where:
        VALUE                                               | EXPECTED
        null                                                | false
        'sc-abc_1'                                          | false
        'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'         | true
        'sc-abc_1:linux/amd64'                              | true
    }

    def 'should encode single scanId'() {
        expect:
        ScanIds.encode(['sc-abc_1': 'linux/amd64']) == 'sc-abc_1'
    }

    def 'should encode multiple scanIds'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        expect:
        ScanIds.encode(map) == 'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'
    }

    def 'should encode null or empty'() {
        expect:
        ScanIds.encode(null) == null
        ScanIds.encode([:]) == null
    }

    def 'should decode single scanId'() {
        when:
        def result = ScanIds.decode('sc-abc_1')
        then:
        result.size() == 1
        result[0].key == 'sc-abc_1'
        result[0].value == null
    }

    def 'should decode multi scanId'() {
        when:
        def result = ScanIds.decode('sc-abc_1:linux/amd64,sc-def_2:linux/arm64')
        then:
        result.size() == 2
        result[0].key == 'sc-abc_1'
        result[0].value == 'linux/amd64'
        result[1].key == 'sc-def_2'
        result[1].value == 'linux/arm64'
    }

    def 'should decode null or empty'() {
        expect:
        ScanIds.decode(null) == []
        ScanIds.decode('') == []
    }

    @Unroll
    def 'should get primary scanId from: #VALUE'() {
        expect:
        ScanIds.primary(VALUE) == EXPECTED

        where:
        VALUE                                               | EXPECTED
        null                                                | null
        'sc-abc_1'                                          | 'sc-abc_1'
        'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'         | 'sc-abc_1'
    }

    def 'should get all ids'() {
        expect:
        ScanIds.allIds('sc-abc_1') == ['sc-abc_1']
        ScanIds.allIds('sc-abc_1:linux/amd64,sc-def_2:linux/arm64') == ['sc-abc_1', 'sc-def_2']
    }

    def 'should roundtrip encode/decode'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        when:
        def encoded = ScanIds.encode(map)
        def decoded = ScanIds.decode(encoded)

        then:
        decoded.size() == 2
        decoded[0].key == 'sc-abc_1'
        decoded[0].value == 'linux/amd64'
        decoded[1].key == 'sc-def_2'
        decoded[1].value == 'linux/arm64'
    }

    def 'should populate scan binding for single scanId'() {
        given:
        def binding = new HashMap()

        when:
        ScanIds.populateScanBinding(binding, 'sc-abc_1', true, 'https://wave.io')
        then:
        binding.scan_id == 'sc-abc_1'
        binding.scan_url == 'https://wave.io/view/scans/sc-abc_1'
        binding.scan_entries == null
    }

    def 'should populate scan binding for multi scanId'() {
        given:
        def binding = new HashMap()

        when:
        ScanIds.populateScanBinding(binding, 'sc-abc_1:linux/amd64,sc-def_2:linux/arm64', true, 'https://wave.io')
        then:
        binding.scan_id == 'sc-abc_1'
        binding.scan_url == null
        binding.scan_entries.size() == 2
        binding.scan_entries[0].scan_id == 'sc-abc_1'
        binding.scan_entries[0].scan_platform == 'linux/amd64'
        binding.scan_entries[0].scan_url == 'https://wave.io/view/scans/sc-abc_1'
        binding.scan_entries[1].scan_id == 'sc-def_2'
        binding.scan_entries[1].scan_platform == 'linux/arm64'
    }

    def 'should populate scan binding when not succeeded'() {
        given:
        def binding = new HashMap()

        when:
        ScanIds.populateScanBinding(binding, 'sc-abc_1:linux/amd64,sc-def_2:linux/arm64', false, 'https://wave.io')
        then:
        binding.scan_id == 'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'
        binding.scan_url == null
        binding.scan_entries == null
    }

    def 'should populate scan binding when scanId is null'() {
        given:
        def binding = new HashMap()

        when:
        ScanIds.populateScanBinding(binding, null, true, 'https://wave.io')
        then:
        binding.scan_id == null
        binding.scan_url == null
    }
}
