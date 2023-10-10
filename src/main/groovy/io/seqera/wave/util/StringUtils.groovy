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
