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
