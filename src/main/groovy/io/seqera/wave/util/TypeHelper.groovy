package io.seqera.wave.util

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import groovy.transform.CompileStatic

/**
 * Helper class to handle Java types
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class TypeHelper {

    static Type getGenericType(Object object, int index) {
        final params = (ParameterizedType) (object.getClass().getGenericSuperclass());
        return params.getActualTypeArguments()[index]
    }

}
