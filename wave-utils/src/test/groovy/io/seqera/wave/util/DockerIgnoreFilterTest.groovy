/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.util

import java.nio.file.Files

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path
/**
 *
 * @author Munish Chouhan munish.chouhan@seqera.io
 */
class DockerIgnoreFilterTest extends Specification {

    @Unroll
    def 'should filter the paths based on ignore patterns'(){
        given:
        List<String> ignorePatterns = [
                "*/ignore*",
                "  */*/exclude*",
                "main.??  ",
                "*.md",
                "!README.md"
        ]
        def pathFilter = DockerIgnoreFilter.from(ignorePatterns)

        expect:
        pathFilter.test(Path.of(PATH)) == ACCEPTED

        where:
        PATH                    | ACCEPTED
        'this/ignore'           | false
        'this/that/exclude.txt' | false
        'this/hola.txt'         | true
        'this/hello.txt'        | true
        'this/that/ciao.txt'    | true
        'main.nf'               | false
        'main.txt'              | true
        'file.md'               | false
        'README.md'             | true
    }

    @Unroll
    def 'should ignore everything'(){
        given:
        List<String> ignorePatterns = ["**"]
        def pathFilter = DockerIgnoreFilter.from(ignorePatterns)

        expect:
        pathFilter.test(Path.of(PATH)) == ACCEPTED

        where:
        PATH                    | ACCEPTED
        'this/ignore'           | false
        'this/that/exclude.txt' | false
        'this/hola.txt'         | false
        'this/hello.txt'        | false
        'this/that/ciao.txt'    | false
        'main.nf'               | false
        'main.txt'              | false
        'file.md'               | false
        'README.md'             | false
    }

    @Unroll
    def 'should filter the paths based on ignore and exception patterns'(){
        given:
        List<String> ignorePatterns = [
                "*/ignore*",
                "main.??",
                "*/*/exclude*",
                "!README.md",
                "*.md"]
        and:
        def pathFilter = DockerIgnoreFilter.from(ignorePatterns)

        expect:
        pathFilter.test(Path.of(PATH)) == ACCEPTED

        where:
        PATH                    | ACCEPTED
        'this/ignore'           | false
        'this/that/exclude.txt' | false
        'this/hola.txt'         | true
        'this/hello.txt'        | true
        'this/that/ciao.txt'    | true
        'main.nf'               | false
        'main.txt'              | true
        'file.md'               | false
        'README.md'             | false
    }

    @Unroll
    def 'should not include readme secret' () {
        given:
        def globs = [
                "*.md",
                "!README*.md",
                "README-secret.md"
        ]
        and:
        def filter = DockerIgnoreFilter.from(globs)

        expect:
        filter.test(Path.of(PATH)) == ACCEPT

        where:
        PATH                    | ACCEPT
        'one.md'                | false
        'two.md'                | false
        and:
        'README-secret.md'      | false
        and:
        'README.md'             | true
        'README1.md'            | true
        'README2.md'            | true
        and:
        'other.txt'             | true
    }

    @Unroll
    def 'should include all readme' () {
        given:
        def globs = [
                "*.md",
                "README-secret.md",
                "!README*.md"
        ]
        and:
        def filter = DockerIgnoreFilter.from(globs)

        expect:
        filter.test(Path.of(PATH)) == ACCEPT

        where:
        PATH                    | ACCEPT
        'one.md'                | false
        'two.md'                | false
        and:
        'README-secret.md'      | true
        and:
        'README.md'             | true
        'README1.md'            | true
        'README2.md'            | true
        and:
        'other.txt'             | true
    }

    def 'should parse dockerignore file' () {
        given:
        def folder = Files.createTempDirectory('test')
        folder.resolve('.docker.ignore').text = '''\
        # some comment
        *.md
         !README*.md
        README-secret.md
        '''.stripIndent()

        when:
        def filter = DockerIgnoreFilter.fromFile(folder.resolve('.docker.ignore'))
        then:
        filter.ignoreGlobs() == ['*.md', '!README*.md', 'README-secret.md']

        cleanup:
        folder?.deleteDir()
    }
}
