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
/**
 * Marker interface to annotation exception bringing
 * a semantic message that can be visualised in the UI layer
 *  
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class WaveException extends RuntimeException {
    WaveException() {}

    WaveException(String message) { super(message) }

    WaveException(String message, Throwable cause) { super(message, cause) }
}
