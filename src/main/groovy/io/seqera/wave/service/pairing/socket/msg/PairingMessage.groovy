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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Define common interface for Wave pairing exchange objects
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes([
        @JsonSubTypes.Type(value = ProxyHttpRequest.class, name = "proxy-http-request"),
        @JsonSubTypes.Type(value = ProxyHttpResponse.class, name = "proxy-http-response"),
        @JsonSubTypes.Type(value = PairingResponse.class, name = "pairing-response"),
        @JsonSubTypes.Type(value = PairingHeartbeat.class, name = "pairing-heartbeat")
])
interface PairingMessage {
    String getMsgId()
}
