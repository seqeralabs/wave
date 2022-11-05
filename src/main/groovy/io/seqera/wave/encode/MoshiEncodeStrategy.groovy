package io.seqera.wave.encode

import java.lang.reflect.Type

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.wave.storage.reader.ContentReader
import io.seqera.wave.storage.reader.DataContentReader
import io.seqera.wave.storage.reader.GzipContentReader
import io.seqera.wave.storage.reader.HttpContentReader
import io.seqera.wave.util.TypeHelper
/**
 * Implements a JSON {@link EncodeStrategy} based on Mosh JSON serializer
 *
 * See https://github.com/square/moshi
 * https://www.baeldung.com/java-json-moshi
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class MoshiEncodeStrategy <V>implements EncodeStrategy<V> {

    private Type type;
    private Moshi moshi
    private JsonAdapter<V> jsonAdapter

    {
        this.type = TypeHelper.getGenericType(this, 0)
        this.moshi = new Moshi.Builder()
                .add(new ByteArrayAdapter())
                .add(new DateTimeAdapter())
                .add(PolymorphicJsonAdapterFactory.of(DigestStore.class, "@type")
                        .withSubtype(LazyDigestStore, LazyDigestStore.simpleName)
                        .withSubtype(ZippedDigestStore, ZippedDigestStore.simpleName) )
                .add(PolymorphicJsonAdapterFactory.of(ContentReader.class, "@type")
                        .withSubtype(DataContentReader.class, DataContentReader.simpleName)
                        .withSubtype(GzipContentReader.class, GzipContentReader.simpleName)
                        .withSubtype(HttpContentReader.class, HttpContentReader.simpleName))
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
