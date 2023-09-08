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

import java.util.regex.Pattern

import groovy.transform.CompileStatic
/**
 * String utils methods
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class StringUtils {

    static String redact(Object value) {
        if( value==null )
            return '(null)'
        if( !value )
            return ('(empty)')
        final str = value.toString()
        return str.length()>=5 ? str[0..2] + '****' : '****'
    }

    static String trunc(String value) {
        if( !value ) return value
        final lines = value.readLines()
        return lines.size()==1 ? lines[0] : lines[0] + ' (more omitted)'
    }

    static String indent(String text) {
        if( !text ) return text
        final result = new StringBuilder()
        for( String line : text.readLines()) {
            result.append(' ').append(line).append('\n')
        }
        return result.toString()
    }

    static final public Pattern URL_PROTOCOL = ~/^([a-zA-Z0-9]*):\\/\\/(.+)/

    static String getUrlProtocol(String str) {
        final m = URL_PROTOCOL.matcher(str)
        return m.matches() ? m.group(1) : null
    }

    static String surrealId(String id) {
        if( !id )
            return null
        final p = id.indexOf(':')
        if( p!=-1 )
            id = id.substring(p+1)
        if( id.startsWith('⟨') && id.endsWith('⟩'))
            id = id.substring(1,id.length()-1)
        return id
    }
}
