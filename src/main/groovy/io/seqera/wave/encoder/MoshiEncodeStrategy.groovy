/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.encoder

import java.lang.reflect.Type

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import io.seqera.serde.encode.StringEncodingStrategy
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.DockerDigestStore
import io.seqera.wave.storage.HttpDigestStore
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.lang.type.TypeHelper
/**
 * Implements a JSON {@link StringEncodingStrategy} based on Mosh JSON serializer
 *
 * See https://github.com/square/moshi
 * https://www.baeldung.com/java-json-moshi
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class MoshiEncodeStrategy<V> implements StringEncodingStrategy<V> {

    private Type type;
    private Moshi moshi
    private JsonAdapter<V> jsonAdapter

    MoshiEncodeStrategy() {
        this.type = TypeHelper.getGenericType(this, 0)
        init()
    }

    MoshiEncodeStrategy(JsonAdapter.Factory customFactory) {
        this.type = TypeHelper.getGenericType(this, 0)
        init(customFactory)
    }

    MoshiEncodeStrategy(Type type) {
        this.type = type
        init()
    }

    private void init(JsonAdapter.Factory customFactory=null) {
        final builder = new Moshi.Builder()
                .add(new ByteArrayAdapter())
                .add(new DateTimeAdapter())
                .add(new PathAdapter())
                .add(new UriAdapter())
                .add(PolymorphicJsonAdapterFactory.of(DigestStore.class, "@type")
                        .withSubtype(ZippedDigestStore, ZippedDigestStore.simpleName)
                        .withSubtype(HttpDigestStore, HttpDigestStore.simpleName)
                        .withSubtype(DockerDigestStore, DockerDigestStore.simpleName) )
                .add(PolymorphicJsonAdapterFactory.of(PairingMessage.class, "@type")
                        .withSubtype(ProxyHttpRequest.class, ProxyHttpRequest.simpleName)
                        .withSubtype(ProxyHttpResponse.class, ProxyHttpResponse.simpleName)
                        .withSubtype(PairingHeartbeat.class, PairingHeartbeat.simpleName)
                        .withSubtype(PairingResponse.class, PairingResponse.simpleName) )
        // add custom factory if provider
        if( customFactory )
            builder.add(customFactory)
        this.moshi = builder.build()
        this.jsonAdapter = moshi.adapter(type)

    }
    @Override
    String encode(V value) {
        if( value == null ) return null
        return jsonAdapter.toJson(value)
    }

    @Override
    V decode(String value) {
        if( value == null ) return null
        return jsonAdapter.fromJson(value)
    }

}
