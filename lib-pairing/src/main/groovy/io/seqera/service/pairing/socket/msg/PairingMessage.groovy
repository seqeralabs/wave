/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing.socket.msg

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
