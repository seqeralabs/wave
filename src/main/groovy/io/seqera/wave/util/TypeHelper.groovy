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
