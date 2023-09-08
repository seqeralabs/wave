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

package io.seqera.wave.auth

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegistryCredentialsFactoryImplTest extends Specification {

    def 'should check equals and hashcode' () {
        given:
        def factory = new RegistryCredentialsFactoryImpl()

        when:
        def c1 = factory.credentials('foo', 'one')
        def c2 = factory.credentials('foo', 'one')
        def c3 = factory.credentials('foo', 'two')

        then:
        c1 == c2
        c1 != c3
        and:
        c1.hashCode() == c2.hashCode()
        c1.hashCode() != c3.hashCode()
    }
}
