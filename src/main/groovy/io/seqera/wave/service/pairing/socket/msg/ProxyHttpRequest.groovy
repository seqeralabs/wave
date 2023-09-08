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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
/**
 * Model a remote HTTP request send via WebSocket connection
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Canonical
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class ProxyHttpRequest implements PairingMessage {
    String msgId
    String method
    String uri
    String auth
    String body
    Map<String, List<String>> headers
}
