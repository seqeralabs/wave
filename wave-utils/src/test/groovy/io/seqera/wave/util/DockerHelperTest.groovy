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

    /* *********************************************************************************
     * Command validation tests
     * *********************************************************************************/

    def 'should validate valid docker commands' () {
        expect:
        DockerHelper.isValidDockerCommand(COMMAND) == EXPECTED

        where:
        COMMAND                          | EXPECTED
        'RUN apt-get update'             | true
        'RUN apt-get install -y curl'    | true
        'FROM ubuntu:22.04'              | true
        'FROM rocker/r-ver:4.4.1'        | true
        'CMD echo hello'                 | true
        'ENV VAR=value'                  | true
        'COPY . /'                       | true
        'WORKDIR /app'                   | true
        'USER root'                      | true
        and:
        'run apt-get update'             | false  // lowercase not allowed
        'apt-get update'                 | false  // missing keyword
        'invalid command'                | false
        'INVALID echo test'              | false  // invalid keyword
        null                             | false
        ''                               | false
        '   '                            | false
    }

    def 'should validate list of commands' () {
        when:
        DockerHelper.validateCommands(['RUN apt-get update', 'FROM ubuntu:22.04'])
        then:
        noExceptionThrown()

        when:
        DockerHelper.validateCommands(['RUN apt-get update', 'RUN apt-get install -y curl'])
        then:
        noExceptionThrown()
    }

    def 'should reject invalid docker commands' () {
        when:
        DockerHelper.validateCommands(['apt-get update'])
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid Docker command at index 0')
        e.message.contains('apt-get update')
        e.message.contains('must start with a valid Dockerfile instruction keyword')

        when:
        DockerHelper.validateCommands(['RUN apt-get update', 'apt-get install -y curl'])
        then:
        e = thrown(IllegalArgumentException)
        e.message.contains('Invalid Docker command at index 1')
        e.message.contains('apt-get install -y curl')
        e.message.contains('must start with a valid Dockerfile instruction keyword')
    }

    def 'should accept all valid docker keywords' () {
        when:
        DockerHelper.validateCommands(['CMD echo hello'])
        then:
        noExceptionThrown()

        when:
        DockerHelper.validateCommands(['ENV VAR=value'])
        then:
        noExceptionThrown()

        when:
        DockerHelper.validateCommands(['COPY . /'])
        then:
        noExceptionThrown()

        when:
        DockerHelper.validateCommands(['WORKDIR /app', 'USER root', 'EXPOSE 8080'])
        then:
        noExceptionThrown()
    }

    def 'should handle null and empty command lists' () {
        when:
        DockerHelper.validateCommands(null)
        then:
        noExceptionThrown()

        when:
        DockerHelper.validateCommands([])
        then:
        noExceptionThrown()
    }

    def 'should return valid keywords set' () {
        when:
        def keywords = DockerHelper.getValidKeywords()
        then:
        keywords.size() == 17
        keywords.contains('FROM')
        keywords.contains('RUN')
        keywords.contains('CMD')
        keywords.contains('ENV')
        keywords.contains('COPY')
        keywords.contains('ADD')
        keywords.contains('WORKDIR')
        keywords.contains('USER')
        keywords.contains('EXPOSE')
        keywords.contains('LABEL')
        keywords.contains('ENTRYPOINT')
        keywords.contains('VOLUME')
        keywords.contains('ARG')
        keywords.contains('ONBUILD')
        keywords.contains('STOPSIGNAL')
        keywords.contains('HEALTHCHECK')
        keywords.contains('SHELL')
        !keywords.contains('INVALID')
    }

    def 'should validate commands with various formats' () {
        expect:
        DockerHelper.isValidDockerCommand(COMMAND) == EXPECTED

        where:
        COMMAND                                              | EXPECTED
        'RUN apt-get update && apt-get install -y curl'      | true
        'RUN echo "Hello World"'                             | true
        'FROM ubuntu:22.04 AS builder'                       | true
        '  RUN  apt-get update  '                            | true  // with whitespace
        'RUN\tapt-get update'                                | true  // with tab
        and:
        'run apt-get update'                                 | false // lowercase
        'Run apt-get update'                                 | false // mixed case
        'RUNNING apt-get update'                             | false // wrong keyword
        'RUNX apt-get update'                                | false // wrong keyword
    }

}
