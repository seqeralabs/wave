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
