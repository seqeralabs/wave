/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.api

import io.seqera.wave.config.CondaOpts

import spock.lang.Specification
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class PackagesSpecTest extends Specification {

    def 'should check equals and hashcode' () {
        given:
        def packages1 = new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: 'foo', entries: ['bar'], channels: ['1', '2'])
        def packages2 = new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: 'foo', entries: ['bar'], channels: ['1', '2'])

        expect:
        packages1 == packages2

        and:
        packages1.hashCode() == packages2.hashCode()
    }

    def 'should infer the correct type' () {
        given:
        def packages1 = new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: 'foo', entries: ['bar'], channels: ['1', '2'])

        expect:
        packages1.type == PackagesSpec.Type.CONDA
    }

    def 'should set values' () {
        when:
        def spec = new PackagesSpec()
                .withType(PackagesSpec.Type.CONDA)
                .withCondaOpts(new CondaOpts(basePackages: 'base:one'))
                .withChannels(['c1','c2'])
                .withEntries(['p1', 'p2'])
                .withEnvironment('foo-env')
        then:
        spec.type == PackagesSpec.Type.CONDA
        spec.condaOpts == new CondaOpts(basePackages: 'base:one')
        spec.channels == ['c1','c2']
        spec.entries == ['p1', 'p2']
        spec.environment == 'foo-env'
    }
}
