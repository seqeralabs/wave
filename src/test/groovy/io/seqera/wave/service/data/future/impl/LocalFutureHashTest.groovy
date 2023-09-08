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

package io.seqera.wave.service.data.future.impl

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalFutureHashTest extends Specification {

    def 'should set and get a value' () {
        given:
        def queue = new LocalFutureHash()

        expect:
        queue.take('xyz') == null

        when:
        queue.put('xyz', 'hello', null)
        then:
        queue.take('xyz') == 'hello'
        and:
        queue.take('xyz') == null
    }

}
