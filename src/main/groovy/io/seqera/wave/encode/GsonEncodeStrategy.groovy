package io.seqera.wave.encode

import java.lang.reflect.Type

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import io.seqera.wave.util.TypeHelper

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class GsonEncodeStrategy<V>implements EncodeStrategy<V> {

    private Type type;
    private Gson gson

    {
        type = TypeHelper.getGenericType(this, 0)
        gson = new GsonBuilder().create();
    }

    @Override
    String encode(V value) {
        return gson.toJson(value)
    }
                    
    @Override
    V decode(String value) {
        if( value == null )
            return null
        final gson = new GsonBuilder().create()
        return gson.fromJson(value, type)
    }

}
