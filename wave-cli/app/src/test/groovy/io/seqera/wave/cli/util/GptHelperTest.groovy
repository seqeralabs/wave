/*
 * Copyright 2023, Seqera Labs
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

package io.seqera.wave.cli.util

import io.seqera.wave.api.PackagesSpec
import spock.lang.Requires
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GptHelperTest extends Specification {


    def 'should map json to spec'() {
        given:
        def JSON = '''
         {"packages":["multiqc=1.17","samtools"],"channels":["conda-forge"]}
        '''
        when:
        def spec = GptHelper.jsonToPackageSpec(JSON)
        then:
        spec.entries == ['multiqc=1.17', 'samtools']
        spec.channels == ['conda-forge']
    }

    @Requires({ System.getenv('OPENAI_API_KEY') })
    def 'should get a package spec from a prompt' () {
        when:
        def spec = GptHelper.grabPackages("Give me a container image for multiqc 1.15")
        then:
        spec == new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: ['multiqc=1.15'], channels: ['bioconda','conda-forge','defaults'])
    }
}
