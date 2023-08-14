/*
 * Copyright (c) 2019-2020, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.exchange

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

/**
 * Generic error response
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@TupleConstructor
final class ErrorResponse {
    String message

    ErrorResponse(String message) {
        this.message = message
    }

    static String of(String message) {
        new ErrorResponse(message)
    }
}
