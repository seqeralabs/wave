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

/**
 * Tests for DockerHelper file-based utility methods
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerHelperTest extends Specification {

    def 'should trim a string' () {
        expect:
        DockerHelper.trim0(STR) == EXPECTED

        where:
        STR         | EXPECTED
        null        | null
        "foo"       | "foo"
        " foo  "    | "foo"
        "'foo"      | "'foo"
        '"foo'      | '"foo'
        and:
        "'foo'"     | "foo"
        "''foo''"   | "foo"
        " 'foo' "   | "foo"
        " ' foo ' " | " foo "
        and:
        '"foo"'     | 'foo'
        '""foo""'   | 'foo'
        ' "foo" '   | 'foo'
    }

    def 'should convert conda packages to list' () {
        expect:
        DockerHelper.condaPackagesToList(STR) == EXPECTED

        where:
        STR                 | EXPECTED
        "foo"               | ["foo"]
        "foo bar"           | ["foo", "bar"]
        "foo 'bar'"         | ["foo", "bar"]
        "foo    'bar'  "    | ["foo", "bar"]
    }

    def 'should convert pip packages to list' () {
        expect:
        DockerHelper.pipPackagesToList(STR) == EXPECTED

        where:
        STR                 | EXPECTED
        "foo"               | ["foo"]
        "foo bar"           | ["foo", "bar"]
        "foo 'bar'"         | ["foo", "bar"]
        "foo    'bar'  "    | ["foo", "bar"]
    }

    def 'should create conda yaml file' () {
        expect:
        DockerHelper.condaPackagesToCondaYaml("foo=1.0 'bar>=2.0'", null)
            ==  '''\
                dependencies:
                - foo=1.0
                - bar>=2.0
                '''.stripIndent(true)


        DockerHelper.condaPackagesToCondaYaml('foo=1.0 bar=2.0', ['channel_a','channel_b'] )
                ==  '''\
                channels:
                - channel_a
                - channel_b
                dependencies:
                - foo=1.0
                - bar=2.0
                '''.stripIndent(true)

        DockerHelper.condaPackagesToCondaYaml('foo=1.0 bar=2.0 pip:numpy pip:pandas', ['channel_a','channel_b'] )
                ==  '''\
                channels:
                - channel_a
                - channel_b
                dependencies:
                - foo=1.0
                - bar=2.0
                - pip
                - pip:
                  - numpy
                  - pandas
                '''.stripIndent(true)
    }

    def 'should return an error when using invalid pip prefix' () {
        when:
        DockerHelper.condaPackagesToCondaYaml('pip::numpy', [] )
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid pip prefix - Likely you want to use 'pip:numpy' instead of 'pip::numpy'"
    }

    def 'should map pip packages to conda yaml' () {
        expect:
        DockerHelper.condaPackagesToCondaYaml('pip:numpy pip:panda pip:matplotlib', ['defaults']) ==
                '''\
                channels:
                - defaults
                dependencies:
                - pip
                - pip:
                  - numpy
                  - panda
                  - matplotlib
                '''.stripIndent()

        and:
        DockerHelper.condaPackagesToCondaYaml(null, ['foo']) == null
        DockerHelper.condaPackagesToCondaYaml('  ', ['foo']) == null
    }

    def 'should add conda packages to conda yaml /1' () {
        given:
        def text = '''\
         dependencies:
         - foo=1.0
         - bar=2.0
        '''.stripIndent(true)

        when:
        def result = DockerHelper.condaEnvironmentToCondaYaml(text, null)
        then:
        result == '''\
         dependencies:
         - foo=1.0
         - bar=2.0
        '''.stripIndent(true)

        when:
        result = DockerHelper.condaEnvironmentToCondaYaml(text, ['ch1', 'ch2'])
        then:
        result == '''\
             dependencies:
             - foo=1.0
             - bar=2.0
             channels:
             - ch1
             - ch2
            '''.stripIndent(true)
    }

    def 'should add conda packages to conda yaml /2' () {
        given:
        def text = '''\
         dependencies:
         - foo=1.0
         - bar=2.0
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        def result = DockerHelper.condaEnvironmentToCondaYaml(text, null)
        then:
        result == '''\
         dependencies:
         - foo=1.0
         - bar=2.0
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        result = DockerHelper.condaEnvironmentToCondaYaml(text, ['ch1', 'ch2'])
        then:
        result == '''\
             dependencies:
             - foo=1.0
             - bar=2.0
             channels:
             - hola
             - ciao
             - ch1
             - ch2
            '''.stripIndent(true)
    }

    def 'should add conda packages to conda yaml /3' () {
        given:
        def text = '''\
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        def result = DockerHelper.condaEnvironmentToCondaYaml(text, null)
        then:
        result == '''\
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        result = DockerHelper.condaEnvironmentToCondaYaml(text, ['ch1', 'ch2'])
        then:
        result == '''\
             channels:
             - hola
             - ciao
             - ch1
             - ch2
            '''.stripIndent(true)

        when:
        result = DockerHelper.condaEnvironmentToCondaYaml(text, ['bioconda'])
        then:
        result == '''\
             channels:
             - hola
             - ciao
             - bioconda
            '''.stripIndent(true)
    }


    def 'should add conda packages to conda file /1' () {
        given:
        def condaFile = Files.createTempFile('conda','yaml')
        condaFile.text = '''\
         dependencies:
         - foo=1.0
         - bar=2.0
        '''.stripIndent(true)

        when:
        def result = DockerHelper.condaFileFromPath(condaFile.toString(), null)
        then:
        result.text == '''\
         dependencies:
         - foo=1.0
         - bar=2.0
        '''.stripIndent(true)

        when:
        result = DockerHelper.condaFileFromPath(condaFile.toString(), ['ch1', 'ch2'])
        then:
        result.text == '''\
             dependencies:
             - foo=1.0
             - bar=2.0
             channels:
             - ch1
             - ch2
            '''.stripIndent(true)

        cleanup:
        if( condaFile ) Files.delete(condaFile)
    }

    def 'should add conda packages to conda file /2' () {
        given:
        def condaFile = Files.createTempFile('conda', 'yaml')
        condaFile.text = '''\
         dependencies:
         - foo=1.0
         - bar=2.0
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        def result = DockerHelper.condaFileFromPath(condaFile.toString(), null)
        then:
        result.text == '''\
         dependencies:
         - foo=1.0
         - bar=2.0
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        result = DockerHelper.condaFileFromPath(condaFile.toString(), ['ch1', 'ch2'])
        then:
        result.text == '''\
             dependencies:
             - foo=1.0
             - bar=2.0
             channels:
             - hola
             - ciao
             - ch1
             - ch2
            '''.stripIndent(true)

        cleanup:
        if (condaFile) Files.delete(condaFile)
    }

    def 'should add conda packages to conda file /3' () {
        given:
        def condaFile = Files.createTempFile('conda', 'yaml')
        condaFile.text = '''\
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        def result = DockerHelper.condaFileFromPath(condaFile.toString(), null)
        then:
        result.text == '''\
         channels:
         - hola
         - ciao
        '''.stripIndent(true)

        when:
        result = DockerHelper.condaFileFromPath(condaFile.toString(), ['ch1', 'ch2'])
        then:
        result.text == '''\
             channels:
             - hola
             - ciao
             - ch1
             - ch2
            '''.stripIndent(true)

        when:
        result = DockerHelper.condaFileFromPath(condaFile.toString(), ['bioconda'])
        then:
        result.text == '''\
             channels:
             - hola
             - ciao
             - bioconda
            '''.stripIndent(true)

        cleanup:
        if (condaFile) Files.delete(condaFile)
    }

    def 'should create conda file from packages' () {
        when:
        def result = DockerHelper.condaFileFromPackages('foo=1.0 bar=2.0', ['conda-forge'])
        then:
        result.text == '''\
            channels:
            - conda-forge
            dependencies:
            - foo=1.0
            - bar=2.0
            '''.stripIndent(true)

        cleanup:
        if (result) Files.deleteIfExists(result)
    }

    def 'should return null for empty packages' () {
        expect:
        DockerHelper.condaFileFromPackages(null, ['conda-forge']) == null
        DockerHelper.condaFileFromPackages('', ['conda-forge']) == null
        DockerHelper.condaFileFromPackages('  ', ['conda-forge']) == null
    }

}
