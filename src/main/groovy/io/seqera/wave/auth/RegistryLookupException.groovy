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

import groovy.transform.CompileStatic
import io.seqera.wave.exception.WaveException
/**
 * Generic registry authorization exception
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegistryLookupException extends WaveException {
    RegistryLookupException(String message) {
        super(message)
    }

    RegistryLookupException(String message, Throwable t) {
        super(message, t)
    }
}
