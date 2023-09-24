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

package io.seqera.wave.storage.reader

/**
 * Capture a HTTP response error that should be manage retrying the HTTP request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class HttpServerRetryableErrorException extends IOException {

    HttpServerRetryableErrorException(String message) {
        super(message)
    }

    HttpServerRetryableErrorException(String message, Throwable t) {
        super(message,t)
    }

}
