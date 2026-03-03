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

/**
 * Tests for {@link ChildEntries}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ChildEntriesTest extends Specification {

    def 'should create from map with single entry'() {
        when:
        def result = ChildEntries.of(['sc-abc_1': 'linux/amd64'])
        then:
        result.size() == 1
        result[0].id == 'sc-abc_1'
        result[0].platform == 'linux/amd64'
    }

    def 'should create from map with multiple entries'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        when:
        def result = ChildEntries.of(map)
        then:
        result.size() == 2
        result[0].id == 'sc-abc_1'
        result[0].platform == 'linux/amd64'
        result[1].id == 'sc-def_2'
        result[1].platform == 'linux/arm64'
    }

    def 'should return null from null or empty map'() {
        expect:
        ChildEntries.of(null) == null
        ChildEntries.of([:]) == null
    }

    def 'should create from list of entries'() {
        when:
        def result = new ChildEntries([
                new ChildEntries.Entry('sc-abc_1', 'linux/amd64'),
                new ChildEntries.Entry('sc-def_2', 'linux/arm64')
        ])
        then:
        result.size() == 2
        result[0].id == 'sc-abc_1'
        result[0].platform == 'linux/amd64'
        result[1].id == 'sc-def_2'
        result[1].platform == 'linux/arm64'
    }

    def 'should get primary id'() {
        expect:
        new ChildEntries([new ChildEntries.Entry('sc-abc_1', null)]).primary() == 'sc-abc_1'
        and:
        new ChildEntries([
                new ChildEntries.Entry('sc-abc_1', 'linux/amd64'),
                new ChildEntries.Entry('sc-def_2', 'linux/arm64')
        ]).primary() == 'sc-abc_1'
        and:
        new ChildEntries([]).primary() == null
    }

    def 'should get all ids'() {
        expect:
        new ChildEntries([new ChildEntries.Entry('sc-abc_1', null)]).allIds() == ['sc-abc_1']
        and:
        new ChildEntries([
                new ChildEntries.Entry('sc-abc_1', 'linux/amd64'),
                new ChildEntries.Entry('sc-def_2', 'linux/arm64')
        ]).allIds() == ['sc-abc_1', 'sc-def_2']
    }

    def 'should roundtrip via of'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        when:
        def result = ChildEntries.of(map)

        then:
        result.size() == 2
        result[0].id == 'sc-abc_1'
        result[0].platform == 'linux/amd64'
        result[1].id == 'sc-def_2'
        result[1].platform == 'linux/arm64'
    }

    def 'should support groovy truth'() {
        expect:
        new ChildEntries([new ChildEntries.Entry('sc-abc_1', null)]) as boolean == true
        new ChildEntries(null) as boolean == false
        new ChildEntries([]) as boolean == false
    }

    def 'should support equality'() {
        expect:
        new ChildEntries([new ChildEntries.Entry('sc-abc_1', 'linux/amd64')]) == new ChildEntries([new ChildEntries.Entry('sc-abc_1', 'linux/amd64')])
        new ChildEntries([new ChildEntries.Entry('sc-abc_1', null)]) != new ChildEntries([new ChildEntries.Entry('sc-def_2', null)])
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
        def scanChildIds = new ChildEntries([
                new ChildEntries.Entry('sc-abc_1', 'linux/amd64'),
                new ChildEntries.Entry('sc-def_2', 'linux/arm64')
        ])

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
        def scanChildIds = new ChildEntries([
                new ChildEntries.Entry('sc-abc_1', 'linux/amd64'),
                new ChildEntries.Entry('sc-def_2', 'linux/arm64')
        ])

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
        def buildChildIds = new ChildEntries([
                new ChildEntries.Entry('bd-abc_0', 'linux/amd64'),
                new ChildEntries.Entry('bd-def_0', 'linux/arm64')
        ])

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
        def original = new ChildEntries([
                new ChildEntries.Entry('sc-abc_1', 'linux/amd64'),
                new ChildEntries.Entry('sc-def_2', 'linux/arm64')
        ])

        when:
        def json = mapper.writeValueAsString(original)
        def restored = mapper.readValue(json, ChildEntries)

        then:
        json == '[{"id":"sc-abc_1","platform":"linux/amd64"},{"id":"sc-def_2","platform":"linux/arm64"}]'
        restored == original
        restored.size() == 2
    }

    def 'should deserialise from json string'() {
        given:
        def mapper = new ObjectMapper()

        expect:
        mapper.readValue(JSON, ChildEntries) == EXPECTED

        where:
        JSON                                                                                    | EXPECTED
        '[]'                                                                                    | new ChildEntries([])
        '[{"id":"sc-1","platform":"linux/amd64"}]'                                              | new ChildEntries([new ChildEntries.Entry('sc-1', 'linux/amd64')])
        '[{"id":"sc-1","platform":"linux/amd64"},{"id":"sc-2","platform":"linux/arm64"}]'        | new ChildEntries([new ChildEntries.Entry('sc-1', 'linux/amd64'), new ChildEntries.Entry('sc-2', 'linux/arm64')])
        '[{"id":"sc-1","platform":null}]'                                                       | new ChildEntries([new ChildEntries.Entry('sc-1', null)])
    }

    def 'should serialise to json string'() {
        given:
        def mapper = new ObjectMapper()

        expect:
        mapper.writeValueAsString(INPUT) == EXPECTED

        where:
        INPUT                                                                                                               | EXPECTED
        new ChildEntries([])                                                                                                | '[]'
        new ChildEntries([new ChildEntries.Entry('sc-1', 'linux/amd64')])                                                   | '[{"id":"sc-1","platform":"linux/amd64"}]'
        new ChildEntries([new ChildEntries.Entry('sc-1', 'linux/amd64'), new ChildEntries.Entry('sc-2', 'linux/arm64')])     | '[{"id":"sc-1","platform":"linux/amd64"},{"id":"sc-2","platform":"linux/arm64"}]'
        new ChildEntries([new ChildEntries.Entry('sc-1', null)])                                                            | '[{"id":"sc-1","platform":null}]'
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
