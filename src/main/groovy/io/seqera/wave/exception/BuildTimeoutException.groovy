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

package io.seqera.wave.exception

import groovy.transform.CompileStatic


/**
 *
 * Exception fired when the time to build an image is expired
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
class BuildTimeoutException extends WaveException{

    BuildTimeoutException(String message) {
        super(message)
    }
}
