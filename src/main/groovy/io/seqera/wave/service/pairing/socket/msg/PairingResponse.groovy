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

package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model the response for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class PairingResponse implements PairingMessage {
    String msgId
    String pairingId
    String publicKey
}
