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

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Paths

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class EscapeTest extends Specification {

    def 'should escape quotes in file names' () {
        expect:
        Escape.path(Paths.get('hello.txt')) == "hello.txt"
        Escape.path(Paths.get("hello'3.txt")) == "hello\\'3.txt"
        Escape.path(Paths.get("hello'3.txt")).size() == "hello'3.txt".size()+1
        Escape.path(Paths.get("hello(3).txt")) == "hello\\(3\\).txt"
        Escape.path(Paths.get("hello\\3.txt")) == "hello\\\\3.txt"
        Escape.path(Paths.get("/some'5/data'3/with/quote's/file's.txt")) == "/some\\'5/data\\'3/with/quote\\'s/file\\'s.txt"
    }

    def 'should escape quote in file names as string' () {
        given:
        String world = 'world'

        expect:
        Escape.path('hello.txt') == "hello.txt"
        Escape.path("hello'3.txt") == "hello\\'3.txt"
        Escape.path("hello'3.txt").size() == "hello'3.txt".size()+1
        Escape.path("hello(3).txt") == "hello\\(3\\).txt"
        Escape.path("hello!3.txt") == "hello\\!3.txt"
        Escape.path('hello[!x].txt') == 'hello[!x].txt' // <-- this `!` should not be escaped because it's a glob negation http://man7.org/linux/man-pages/man7/glob.7.html
        Escape.path('hello[x!].txt') == 'hello[x\\!].txt'
        Escape.path('hello[!x.txt') == 'hello[\\!x.txt'
        Escape.path('hello![x,*.txt]') == 'hello\\![x,*.txt]'
        Escape.path("hello&3.txt") == "hello\\&3.txt"
        Escape.path("hello<3.txt") == "hello\\<3.txt"
        Escape.path("hello>3.txt") == "hello\\>3.txt"
        Escape.path("hello`3.txt") == "hello\\`3.txt"
        Escape.path("/some'5/data'3/with/quote's/file's.txt") == "/some\\'5/data\\'3/with/quote\\'s/file\\'s.txt"
        Escape.path("Hello '$world'") == "Hello\\ \\'world\\'"

    }

    def 'should escape wildcards' () {

        expect: 
        Escape.wildcards('file_*') == 'file_\\*'
        Escape.wildcards('file_??') == 'file_\\?\\?'
        Escape.wildcards('file_{a,b}') == 'file_\\{a,b\\}'
        Escape.wildcards('file_!a.txt') == 'file_\\!a.txt'
        Escape.wildcards('file_[!a].txt') == 'file_\\[\\!a\\].txt'
    }

    def 'should escape cli' () {
        expect: 
        Escape.cli('nextflow','run','this') == 'nextflow run this'
        Escape.cli('nextflow','--foo','file.txt') == 'nextflow --foo file.txt'
        Escape.cli('nextflow','--foo','*.txt') == "nextflow --foo '*.txt'"
        Escape.cli('nextflow','--foo','*_{1,2}.fq') == "nextflow --foo '*_{1,2}.fq'"
        Escape.cli('nextflow','--foo','a b c') == "nextflow --foo 'a b c'"
        Escape.cli('nextflow','--foo','ab\'c') == "nextflow --foo ab\\'c"
        Escape.cli('nextflow','--foo','a[b-c]') == "nextflow --foo 'a[b-c]'"
        Escape.cli('nextflow','--foo','a[!b-c]') == "nextflow --foo 'a[!b-c]'"
    }

    @Unroll
    def 'should escape blanks' () {
        expect:
        Escape.blanks(STR) == EXPECT
        where:
        STR         | EXPECT
        'foo '      | 'foo '
        'foo\n'     | 'foo\\n'
        'foo\t'     | 'foo\\t'
        'foo\f'     | 'foo\\f'
        'foo\r'     | 'foo\\r'
    }

    def 'should escape special char' () {
        expect:
        Escape.variable(STR) == EXPECT
        where:
        STR         | EXPECT
        'foo'       | 'foo'
        'foo[x]bar' | 'foo[x]bar'
        'foo '      | 'foo '
        'foo:bar'   | 'foo:bar'
        'foo!bar'   | 'foo!bar'
        'foo[!x]bar'| 'foo[!x]bar'
        and:
        '$foo'      | '\\$foo'
        'foo|bar'   | 'foo\\|bar'
        'foo`bar'   | 'foo\\`bar'
        'foo&bar'   | 'foo\\&bar'
        'foo(x)bar' | 'foo\\(x\\)bar'
        'foo<x>bar' | 'foo\\<x\\>bar'
    }
}
