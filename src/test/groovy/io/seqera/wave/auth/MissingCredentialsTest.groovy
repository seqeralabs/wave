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
class MissingCredentialsTest extends Specification {

    def 'should check equals and hash code' () {
        given:
        def c1 = new MissingCredentials('a')
        def c2 = new MissingCredentials('a')
        def c3 = new MissingCredentials('z')

        expect:
        c1 == c2
        c1 != c3
        and:
        c1.hashCode() == c2.hashCode()
        c1.hashCode() != c3.hashCode()
    }

    def 'should check groovy truth' () {
        expect:
        !new MissingCredentials('a')
    }
}
