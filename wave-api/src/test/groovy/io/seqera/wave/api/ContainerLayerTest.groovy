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
class ContainerLayerTest extends Specification {

    def 'should check equals and hashcode'() {
        given:
        def l1 = new ContainerLayer( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def l2 = new ContainerLayer( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def l3 = new ContainerLayer( 'http://bar.com', 'sha256:abc', 200, 'sha256:abf' )

        expect:
        l1 == l2
        l1 != l3
        and:
        l1.hashCode() == l2.hashCode()
        l1.hashCode() != l3.hashCode()
    }

    def 'should copy layer' () {
        given:
        def l1 = new ContainerLayer( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        
        expect:
        null == ContainerLayer.copy(null)
        and:
        l1 == ContainerLayer.copy(l1)
        l1 == ContainerLayer.copy(l1,false)
        l1 == ContainerLayer.copy(l1,true)
    }

    def 'should copy layer stripping data' () {
        given:
        def l1 = new ContainerLayer( 'data:ABC1234567890', 'sha256:12345', 100, 'sha256:67890' )

        expect:
        l1 == ContainerLayer.copy(l1)
        l1 == ContainerLayer.copy(l1,false)
        l1 != ContainerLayer.copy(l1,true)
        and:
        def l2 = ContainerLayer.copy(l1,true)
        l2.location == 'data:DATA+OMITTED'
        l2.tarDigest == l1.tarDigest
        l2.gzipDigest == l1.gzipDigest
        l2.gzipSize == l1.gzipSize
    }

    def 'should convert to string' () {
        when:
        def l1 = new ContainerLayer( 'data:ABC1234567890', 'sha256:12345', 100, 'sha256:67890' )
        then:
        l1.toString() == 'ContainerLayer[location=data:ABC1234567890; tarDigest=sha256:67890; gzipDigest=sha256:12345; gzipSize=100]'

        when:
        def l2 = new ContainerLayer( 'data:12345678901234567890', 'sha256:12345', 100, 'sha256:67890' )
        then:
        l2.toString() == 'ContainerLayer[location=data:12345678901234567890; tarDigest=sha256:67890; gzipDigest=sha256:12345; gzipSize=100]'

        when:
        def l3= new ContainerLayer( 'data:12345678901234567890x', 'sha256:12345', 100, 'sha256:67890' )
        then:
        l3.toString() == 'ContainerLayer[location=data:12345678901234567890...; tarDigest=sha256:67890; gzipDigest=sha256:12345; gzipSize=100]'

    }
}
