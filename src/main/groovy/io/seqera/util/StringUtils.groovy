package io.seqera.util

import groovy.transform.CompileStatic

/**
 * String utils methods
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class StringUtils {

    static String redact(Object value) {
        final str = value.toString()
        return str.length()>=5 ? str[0..2] + '****' : '****'
    }

}
