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

import spock.lang.Specification
import spock.lang.Unroll

class BuildTemplateTest extends Specification {

    @Unroll
    def 'should return default template for #PACKAGES'() {
        expect:
        BuildTemplate.defaultTemplate(PACKAGES) == EXPECTED

        where:
        PACKAGES                                              | EXPECTED
        new PackagesSpec(type: PackagesSpec.Type.CRAN)        | BuildTemplate.CRAN_INSTALLR_V1
        new PackagesSpec(type: PackagesSpec.Type.CONDA)       | null 
        null                                                  | null
        new PackagesSpec()                                    | null
    }
}
