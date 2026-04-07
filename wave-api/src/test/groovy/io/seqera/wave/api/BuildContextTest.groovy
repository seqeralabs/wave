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

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildContextTest extends Specification {

    def 'should check equals and hashcode' () {
        given:
        def l1 = new BuildContext( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def l2 = new BuildContext( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def l3 = new BuildContext( 'http://bar.com', 'sha256:88788', 100, 'sha256:67890' )

        expect:
        l1 == l2
        l1 != l3
        and:
        l1.hashCode() == l2.hashCode()
        l1.hashCode() != l3.hashCode()
    }

    def 'should create build context from container layer' () {
        given:
        def l1 = new BuildContext( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        when:
        def b1 = BuildContext.of(l1)
        then:
        b1 == l1
    }

}
