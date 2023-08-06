/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
