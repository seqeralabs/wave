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

package io.seqera.wave.model

import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.IndexSpec
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerOrIndexSpecTest extends Specification {

    def 'should create a spec with a index' () {
        given:
        def index = Mock(IndexSpec)
        def spec = new ContainerOrIndexSpec(index)
        expect:
        spec.getIndex() == index
        !spec.getContainer()
    }

    def 'should create a spec with a container' () {
        given:
        def container = Mock(ContainerSpec)
        def spec = new ContainerOrIndexSpec(container)
        expect:
        spec.getContainer() == container
        !spec.getIndex()
    }

}
