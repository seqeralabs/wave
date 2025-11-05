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

import io.seqera.wave.config.CondaOpts

import spock.lang.Specification
/**
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
    
    def 'should create dockerfile content from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::bar'])

        expect:
        DockerHelper.condaFileToDockerFile(CONDA_OPTS)== '''\
                FROM mambaorg/micromamba:1.5.10-noble
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base foo::bar \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile content from conda file and base packages' () {

        expect:
        DockerHelper.condaFileToDockerFile(new CondaOpts([:]))== '''\
                FROM mambaorg/micromamba:1.5.10-noble
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }


    def 'should create dockerfile content from conda package' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'defaults']
        expect:
        DockerHelper.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile with base packages' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        DockerHelper.condaPackagesToDockerFile(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base foo::one bar::two \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile content with custom channels' () {
        given:
        def CHANNELS = 'foo,bar'.tokenize(',')
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        DockerHelper.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c foo -c bar bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile content with custom conda config' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = [mambaImage:'my-base:123', commands: ['USER my-user', 'RUN apt-get update -y && apt-get install -y nano']]
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        DockerHelper.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts(CONDA_OPTS)) == '''\
                FROM my-base:123
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                USER my-user
                RUN apt-get update -y && apt-get install -y nano
                '''.stripIndent()
    }


    def 'should create dockerfile content with remote conda lock' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def OPTS = [mambaImage:'my-base:123', commands: ['USER my-user', 'RUN apt-get update -y && apt-get install -y procps']]
        def PACKAGES = 'https://foo.com/some/conda-lock.yml'

        expect:
        DockerHelper.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts(OPTS)) == '''\
                FROM my-base:123
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/some/conda-lock.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                USER my-user
                RUN apt-get update -y && apt-get install -y procps
                '''.stripIndent()
    }


    /* *********************************************************************************
     * conda packages to singularity tests
     * *********************************************************************************/

    def 'should create singularity content from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::bar=1.0'])

        expect:
        DockerHelper.condaFileToSingularityFile(CONDA_OPTS)== '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base foo::bar=1.0
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity content from conda file and base packages' () {

        expect:
        DockerHelper.condaFileToSingularityFile(new CondaOpts([:]))== '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }


    def 'should create singularity content from conda package' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'defaults']
        expect:
        DockerHelper.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity with base packages' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        DockerHelper.condaPackagesToSingularityFile(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base foo::one bar::two
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity content with custom channels' () {
        given:
        def CHANNELS = 'foo,bar'.tokenize(',')
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        DockerHelper.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    micromamba install -y -n base -c foo -c bar bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity content with custom conda config' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = [mambaImage:'my-base:123', commands: ['install --this --that', 'apt-get update -y && apt-get install -y nano']]
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        DockerHelper.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts(CONDA_OPTS)) == '''\
                BootStrap: docker
                From: my-base:123
                %post
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                %post
                    install --this --that
                    apt-get update -y && apt-get install -y nano
                '''.stripIndent()
    }


    def 'should create singularity content with remote conda lock' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def OPTS = [mambaImage:'my-base:123', commands: ['apt-get update -y && apt-get install -y procps']]
        def PACKAGES = 'https://foo.com/some/conda-lock.yml'

        expect:
        DockerHelper.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts(OPTS)) == '''\
                BootStrap: docker
                From: my-base:123
                %post
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/some/conda-lock.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                %post
                    apt-get update -y && apt-get install -y procps
                '''.stripIndent()
    }

}
