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

import com.squareup.moshi.Moshi
import spock.lang.Specification

/**
 * Tests for {@link ChildRefs}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ChildRefsTest extends Specification {

    def 'should create from map with single entry'() {
        when:
        def result = ChildRefs.of(['sc-abc_1': 'linux/amd64'])
        then:
        result.size() == 1
        result[0].id == 'sc-abc_1'
        result[0].value == 'linux/amd64'
    }

    def 'should create from map with multiple entries'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        when:
        def result = ChildRefs.of(map)
        then:
        result.size() == 2
        result[0].id == 'sc-abc_1'
        result[0].value == 'linux/amd64'
        result[1].id == 'sc-def_2'
        result[1].value == 'linux/arm64'
    }

    def 'should return null from null or empty map'() {
        expect:
        ChildRefs.of(null) == null
        ChildRefs.of([:]) == null
    }

    def 'should create from list of entries'() {
        when:
        def result = new ChildRefs([
                new ChildRefs.Ref('sc-abc_1', 'linux/amd64'),
                new ChildRefs.Ref('sc-def_2', 'linux/arm64')
        ])
        then:
        result.size() == 2
        result[0].id == 'sc-abc_1'
        result[0].value == 'linux/amd64'
        result[1].id == 'sc-def_2'
        result[1].value == 'linux/arm64'
    }

    def 'should get primary id'() {
        expect:
        new ChildRefs([new ChildRefs.Ref('sc-abc_1', null)]).primary() == 'sc-abc_1'
        and:
        new ChildRefs([
                new ChildRefs.Ref('sc-abc_1', 'linux/amd64'),
                new ChildRefs.Ref('sc-def_2', 'linux/arm64')
        ]).primary() == 'sc-abc_1'
        and:
        new ChildRefs([]).primary() == null
    }

    def 'should get all ids'() {
        expect:
        new ChildRefs([new ChildRefs.Ref('sc-abc_1', null)]).allIds() == ['sc-abc_1']
        and:
        new ChildRefs([
                new ChildRefs.Ref('sc-abc_1', 'linux/amd64'),
                new ChildRefs.Ref('sc-def_2', 'linux/arm64')
        ]).allIds() == ['sc-abc_1', 'sc-def_2']
    }

    def 'should roundtrip via of'() {
        given:
        def map = new LinkedHashMap<String,String>()
        map.put('sc-abc_1', 'linux/amd64')
        map.put('sc-def_2', 'linux/arm64')

        when:
        def result = ChildRefs.of(map)

        then:
        result.size() == 2
        result[0].id == 'sc-abc_1'
        result[0].value == 'linux/amd64'
        result[1].id == 'sc-def_2'
        result[1].value == 'linux/arm64'
    }

    def 'should support groovy truth'() {
        expect:
        new ChildRefs([new ChildRefs.Ref('sc-abc_1', null)]) as boolean == true
        new ChildRefs(null) as boolean == false
        new ChildRefs([]) as boolean == false
    }

    def 'should support equality'() {
        expect:
        new ChildRefs([new ChildRefs.Ref('sc-abc_1', 'linux/amd64')]) == new ChildRefs([new ChildRefs.Ref('sc-abc_1', 'linux/amd64')])
        new ChildRefs([new ChildRefs.Ref('sc-abc_1', null)]) != new ChildRefs([new ChildRefs.Ref('sc-def_2', null)])
    }

    // -- template binding tests --

    def 'should populate scan binding for single scanId'() {
        given:
        def binding = new HashMap()

        when:
        ChildRefs.populateScanBinding(binding, 'sc-abc_1', null, true, 'https://wave.io')
        then:
        binding.scan_id == 'sc-abc_1'
        binding.scan_url == 'https://wave.io/view/scans/sc-abc_1'
        binding.scan_entries == null
    }

    def 'should populate scan binding for multi-platform scanChildIds'() {
        given:
        def binding = new HashMap()
        def scanChildIds = new ChildRefs([
                new ChildRefs.Ref('sc-abc_1', 'linux/amd64'),
                new ChildRefs.Ref('sc-def_2', 'linux/arm64')
        ])

        when:
        ChildRefs.populateScanBinding(binding, null, scanChildIds, true, 'https://wave.io')
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
        def scanChildIds = new ChildRefs([
                new ChildRefs.Ref('sc-abc_1', 'linux/amd64'),
                new ChildRefs.Ref('sc-def_2', 'linux/arm64')
        ])

        when:
        ChildRefs.populateScanBinding(binding, null, scanChildIds, false, 'https://wave.io')
        then:
        binding.scan_id == null
        binding.scan_url == null
        binding.scan_entries == null
    }

    def 'should populate scan binding when scanId is null'() {
        given:
        def binding = new HashMap()

        when:
        ChildRefs.populateScanBinding(binding, null, null, true, 'https://wave.io')
        then:
        binding.scan_id == null
        binding.scan_url == null
    }

    def 'should populate build binding for child builds'() {
        given:
        def binding = new HashMap()
        def buildChildIds = new ChildRefs([
                new ChildRefs.Ref('bd-abc_0', 'linux/amd64'),
                new ChildRefs.Ref('bd-def_0', 'linux/arm64')
        ])

        when:
        ChildRefs.populateBuildBinding(binding, buildChildIds, 'https://wave.io')
        then:
        binding.build_entries.size() == 2
        binding.build_entries[0].build_id == 'bd-abc_0'
        binding.build_entries[0].build_platform == 'linux/amd64'
        binding.build_entries[0].build_url == 'https://wave.io/view/builds/bd-abc_0'
        binding.build_entries[1].build_id == 'bd-def_0'
        binding.build_entries[1].build_platform == 'linux/arm64'
        binding.build_entries[1].build_url == 'https://wave.io/view/builds/bd-def_0'
    }

    def 'should roundtrip through Moshi serialization'() {
        given:
        def moshi = new Moshi.Builder().build()
        def adapter = moshi.adapter(ChildRefs)
        def original = new ChildRefs([
                new ChildRefs.Ref('sc-abc_1', 'linux/amd64'),
                new ChildRefs.Ref('sc-def_2', 'linux/arm64')
        ])

        when:
        def json = adapter.toJson(original)
        def restored = adapter.fromJson(json)

        then:
        restored == original
        restored.size() == 2
        restored[0].id == 'sc-abc_1'
        restored[0].value == 'linux/amd64'
        restored[1].id == 'sc-def_2'
        restored[1].value == 'linux/arm64'
    }

    def 'should deserialise from Moshi json string'() {
        given:
        def moshi = new Moshi.Builder().build()
        def adapter = moshi.adapter(ChildRefs)

        expect:
        adapter.fromJson(JSON) == EXPECTED

        where:
        JSON                                                                                                        | EXPECTED
        '{"refs":[]}'                                                                                            | new ChildRefs([])
        '{"refs":[{"id":"sc-1","value":"linux/amd64"}]}'                                                      | new ChildRefs([new ChildRefs.Ref('sc-1', 'linux/amd64')])
        '{"refs":[{"id":"sc-1","value":"linux/amd64"},{"id":"sc-2","value":"linux/arm64"}]}'                | new ChildRefs([new ChildRefs.Ref('sc-1', 'linux/amd64'), new ChildRefs.Ref('sc-2', 'linux/arm64')])
    }

    def 'should serialise to Moshi json string'() {
        given:
        def moshi = new Moshi.Builder().build()
        def adapter = moshi.adapter(ChildRefs)

        expect:
        adapter.toJson(INPUT) == EXPECTED

        where:
        INPUT                                                                                                               | EXPECTED
        new ChildRefs([])                                                                                       | '{"refs":[]}'
        new ChildRefs([new ChildRefs.Ref('sc-1', 'linux/amd64')])                                           | '{"refs":[{"id":"sc-1","value":"linux/amd64"}]}'
        new ChildRefs([new ChildRefs.Ref('sc-1', 'linux/amd64'), new ChildRefs.Ref('sc-2', 'linux/arm64')]) | '{"refs":[{"id":"sc-1","value":"linux/amd64"},{"id":"sc-2","value":"linux/arm64"}]}'
    }

    def 'should populate build binding when null'() {
        given:
        def binding = new HashMap()

        when:
        ChildRefs.populateBuildBinding(binding, null, 'https://wave.io')
        then:
        binding.build_entries == null
    }
}
