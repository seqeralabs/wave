/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.LayerDigestStore
import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.wave.storage.reader.ContentReader
import io.seqera.wave.storage.reader.DataContentReader
import io.seqera.wave.storage.reader.GzipContentReader
import io.seqera.wave.storage.reader.HttpContentReader
import io.seqera.wave.storage.reader.PathContentReader
import io.seqera.wave.util.TypeHelper

/**
 * Implements a JSON {@link EncodingStrategy} based on Mosh JSON serializer
 *
 * See https://github.com/square/moshi
 * https://www.baeldung.com/java-json-moshi
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class MoshiEncodeStrategy<V> implements EncodingStrategy<V> {

    private Type type;
    private Moshi moshi
    private JsonAdapter<V> jsonAdapter

    MoshiEncodeStrategy() {
        this.type = TypeHelper.getGenericType(this, 0)
        init()
    }

    MoshiEncodeStrategy(Type type) {
        this.type = type
        init()
    }

    private void init() {
        this.moshi = new Moshi.Builder()
                .add(new ByteArrayAdapter())
                .add(new DateTimeAdapter())
                .add(new PathAdapter())
                .add(PolymorphicJsonAdapterFactory.of(DigestStore.class, "@type")
                        .withSubtype(LazyDigestStore, LazyDigestStore.simpleName)
                        .withSubtype(LayerDigestStore, LayerDigestStore.simpleName)
                        .withSubtype(ZippedDigestStore, ZippedDigestStore.simpleName) )
                .add(PolymorphicJsonAdapterFactory.of(ContentReader.class, "@type")
                        .withSubtype(DataContentReader.class, DataContentReader.simpleName)
                        .withSubtype(GzipContentReader.class, GzipContentReader.simpleName)
                        .withSubtype(HttpContentReader.class, HttpContentReader.simpleName)
                        .withSubtype(PathContentReader.class, PathContentReader.simpleName))
                .add(PolymorphicJsonAdapterFactory.of(PairingMessage.class, "@type")
                        .withSubtype(ProxyHttpRequest.class, ProxyHttpRequest.simpleName)
                        .withSubtype(ProxyHttpResponse.class, ProxyHttpResponse.simpleName)
                        .withSubtype(PairingHeartbeat.class, PairingHeartbeat.simpleName)
                        .withSubtype(PairingResponse.class, PairingResponse.simpleName)
                )
                .build()
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
