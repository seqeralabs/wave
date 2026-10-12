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

package io.seqera.wave.util

import java.nio.file.Path

import groovy.transform.CompileStatic
/**
 * Escape helper class
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class Escape {

    private static List<String> SPECIAL_CHARS = ["'", '"', ' ', '(', ')', '\\', '!', '&', '|', '<', '>', '`', ':', ';']

    private static List<String> VAR_CHARS = ['$', "'", '"', '(', ')', '\\', '&', '|', '<', '>', '`']

    private static List<String> WILDCARDS = ["*", "?", "{", "}", "[", "]", "'", '"', ' ', '(', ')', '\\', '!', '&', '|', '<', '>', '`', ':']

    private static String replace(List<String> special, String str, boolean doNotEscapeComplement=false) {
        def copy = new StringBuilder(str.size() +10)
        for( int i=0; i<str.size(); i++) {
            def ch = str[i]
            def p = special.indexOf(ch)
            if( p != -1 ) {
                // when ! is the first character after a `[` it should not be escaped
                // see http://man7.org/linux/man-pages/man7/glob.7.html
                final isComplement = doNotEscapeComplement && ch=='!' && ( i>0 && str[i-1]=='[' && (i==1 || str[i-2]!='\\') && str.substring(i).contains(']'))
                if( !isComplement )
                    copy.append('\\')
            }
            copy.append(str[i])
        }
        return copy.toString()
    }

    static String wildcards(String str) {
        replace(WILDCARDS, str)
    }

    static String path(String val) {
        replace(SPECIAL_CHARS, val, true)
    }

    static String path(Path val) {
        path(val.toString())
    }

    static String path(File val) {
        path(val.toString())
    }

    static String path(GString val) {
        path(val.toString())
    }

    static String cli(String[] args) {
        args.collect(it -> cli(it)).join(' ')
    }

    static String cli(List<String> args) {
        args.collect(it -> cli(it)).join(' ')
    }

    static String cli(String arg) {
        if( arg.contains("'") )
            return wildcards(arg)
        def x = wildcards(arg)
        x == arg ? arg : "'" + arg + "'"
    }

    static String blanks(String str) {
        str
                .replaceAll('\n',/\\n/)
                .replaceAll('\t',/\\t/)
                .replaceAll('\r',/\\r/)
                .replaceAll('\f',/\\f/)

    }

    static String variable(String val) {
        replace(VAR_CHARS, val, false)
    }
}
