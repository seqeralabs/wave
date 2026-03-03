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

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for {@link ChildEntries}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ChildEntriesTest extends Specification {

    def 'should create from map with single entry'() {
        expect:
        ChildEntries.of(['sc-abc_1': 'linux/amd64']).toString() == 'sc-abc_1:linux/amd64'
    }

    def 'should create from map with multiple entries'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        expect:
        ChildEntries.of(map).toString() == 'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'
    }

    def 'should return null from null or empty map'() {
        expect:
        ChildEntries.of(null) == null
        ChildEntries.of([:]) == null
    }

    def 'should decode single entry'() {
        when:
        def result = new ChildEntries('sc-abc_1:linux/amd64').decode()
        then:
        result.size() == 1
        result[0].key == 'sc-abc_1'
        result[0].value == 'linux/amd64'
    }

    def 'should decode bare id (no platform)'() {
        when:
        def result = new ChildEntries('sc-abc_1').decode()
        then:
        result.size() == 1
        result[0].key == 'sc-abc_1'
        result[0].value == null
    }

    def 'should decode multi entries'() {
        when:
        def result = new ChildEntries('sc-abc_1:linux/amd64,sc-def_2:linux/arm64').decode()
        then:
        result.size() == 2
        result[0].key == 'sc-abc_1'
        result[0].value == 'linux/amd64'
        result[1].key == 'sc-def_2'
        result[1].value == 'linux/arm64'
    }

    @Unroll
    def 'should get primary id from: #ENCODED'() {
        expect:
        new ChildEntries(ENCODED).primary() == EXPECTED

        where:
        ENCODED                                             | EXPECTED
        'sc-abc_1'                                          | 'sc-abc_1'
        'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'         | 'sc-abc_1'
    }

    def 'should get all ids'() {
        expect:
        new ChildEntries('sc-abc_1').allIds() == ['sc-abc_1']
        new ChildEntries('sc-abc_1:linux/amd64,sc-def_2:linux/arm64').allIds() == ['sc-abc_1', 'sc-def_2']
    }

    def 'should roundtrip via of and decode'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        when:
        def entries = ChildEntries.of(map)
        def decoded = entries.decode()

        then:
        decoded.size() == 2
        decoded[0].key == 'sc-abc_1'
        decoded[0].value == 'linux/amd64'
        decoded[1].key == 'sc-def_2'
        decoded[1].value == 'linux/arm64'
    }

    def 'should serialize to string via toString'() {
        given:
        def entries = new ChildEntries('sc-abc_1:linux/amd64,sc-def_2:linux/arm64')
        expect:
        entries.toString() == 'sc-abc_1:linux/amd64,sc-def_2:linux/arm64'
    }

    def 'should support groovy truth'() {
        expect:
        new ChildEntries('sc-abc_1') as boolean == true
        new ChildEntries(null) as boolean == false
        new ChildEntries('') as boolean == false
    }

    def 'should support equality'() {
        expect:
        new ChildEntries('sc-abc_1:linux/amd64') == new ChildEntries('sc-abc_1:linux/amd64')
        !new ChildEntries('sc-abc_1').equals(new ChildEntries('sc-def_2'))
    }

    // -- template binding tests --

    def 'should populate scan binding for single scanId'() {
        given:
        def binding = new HashMap()

        when:
        ChildEntries.populateScanBinding(binding, 'sc-abc_1', null, true, 'https://wave.io')
        then:
        binding.scan_id == 'sc-abc_1'
        binding.scan_url == 'https://wave.io/view/scans/sc-abc_1'
        binding.scan_entries == null
    }

    def 'should populate scan binding for multi-platform scanChildIds'() {
        given:
        def binding = new HashMap()
        def scanChildIds = new ChildEntries('sc-abc_1:linux/amd64,sc-def_2:linux/arm64')

        when:
        ChildEntries.populateScanBinding(binding, null, scanChildIds, true, 'https://wave.io')
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
        def scanChildIds = new ChildEntries('sc-abc_1:linux/amd64,sc-def_2:linux/arm64')

        when:
        ChildEntries.populateScanBinding(binding, null, scanChildIds, false, 'https://wave.io')
        then:
        binding.scan_id == null
        binding.scan_url == null
        binding.scan_entries == null
    }

    def 'should populate scan binding when scanId is null'() {
        given:
        def binding = new HashMap()

        when:
        ChildEntries.populateScanBinding(binding, null, null, true, 'https://wave.io')
        then:
        binding.scan_id == null
        binding.scan_url == null
    }

    def 'should populate build binding for child builds'() {
        given:
        def binding = new HashMap()
        def buildChildIds = new ChildEntries('bd-abc_0:linux/amd64,bd-def_0:linux/arm64')

        when:
        ChildEntries.populateBuildBinding(binding, buildChildIds, 'https://wave.io')
        then:
        binding.build_entries.size() == 2
        binding.build_entries[0].build_id == 'bd-abc_0'
        binding.build_entries[0].build_platform == 'linux/amd64'
        binding.build_entries[0].build_url == 'https://wave.io/view/builds/bd-abc_0'
        binding.build_entries[1].build_id == 'bd-def_0'
        binding.build_entries[1].build_platform == 'linux/arm64'
        binding.build_entries[1].build_url == 'https://wave.io/view/builds/bd-def_0'
    }

    def 'should roundtrip through Jackson serialization'() {
        given:
        def mapper = new ObjectMapper()
        def original = new ChildEntries('sc-abc_1:linux/amd64,sc-def_2:linux/arm64')

        when:
        def json = mapper.writeValueAsString(original)
        def restored = mapper.readValue(json, ChildEntries)

        then:
        json == '"sc-abc_1:linux/amd64,sc-def_2:linux/arm64"'
        restored == original
        restored.decode().size() == 2
    }

    def 'should handle null in Jackson serialization'() {
        given:
        def mapper = new ObjectMapper()

        when:
        def json = mapper.writeValueAsString([entries: null])
        then:
        json == '{"entries":null}'

        when:
        def restored = mapper.readValue('"sc-abc_1:linux/amd64"', ChildEntries)
        then:
        restored == new ChildEntries('sc-abc_1:linux/amd64')
    }

    def 'should populate build binding when null'() {
        given:
        def binding = new HashMap()

        when:
        ChildEntries.populateBuildBinding(binding, null, 'https://wave.io')
        then:
        binding.build_entries == null
    }
}
