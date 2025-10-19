/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.pairing.socket

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse

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
