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

package io.seqera.service.pairing.socket

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.service.pairing.socket.msg.PairingMessage
import io.seqera.service.pairing.socket.msg.PairingResponse
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse

/**
 * Factory class for creating a Moshi-based encoding strategy for polymorphic pairing message types.
 * <p>
 * This class provides a {@link MoshiEncodeStrategy} configured with a polymorphic JSON adapter
 * that enables serialization and deserialization of different {@link PairingMessage} subtypes
 * using a type discriminator field ({@code @type}) in the JSON representation.
 * <p>
 * The following message types are supported:
 * <ul>
 *   <li>{@link ProxyHttpRequest} - HTTP requests forwarded through the pairing connection</li>
 *   <li>{@link ProxyHttpResponse} - HTTP responses returned through the pairing connection</li>
 *   <li>{@link PairingHeartbeat} - Heartbeat messages to maintain connection liveness</li>
 *   <li>{@link PairingResponse} - General pairing response messages</li>
 * </ul>
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PairingMessageEncodeStrategy {

    static MoshiEncodeStrategy<PairingMessage> create() {
        new MoshiEncodeStrategy<PairingMessage>(factory()) {}
    }

    private static JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(PairingMessage.class, "@type")
                .withSubtype(ProxyHttpRequest.class, ProxyHttpRequest.simpleName)
                .withSubtype(ProxyHttpResponse.class, ProxyHttpResponse.simpleName)
                .withSubtype(PairingHeartbeat.class, PairingHeartbeat.simpleName)
                .withSubtype(PairingResponse.class, PairingResponse.simpleName)
    }

}
