/*
 * Copyright 2023, Seqera Labs
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

package io.seqera.wave.cli


import io.seqera.wave.cli.exception.IllegalCliArgumentException
import picocli.CommandLine
import spock.lang.Specification
/**
 * Test App Conda prefixed options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AppPipOptsTest extends Specification {

    def 'should fail when passing both pip and conda packages' () {
        given:
        def app = new App()
        String[] args = ["--pip-package", "foo", "--conda-package", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both pip package and conda file' () {
        given:
        def app = new App()
        String[] args = ["--pip-package", "foo", "--conda-file", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both pip and spack packages' () {
        given:
        def app = new App()
        String[] args = ["--pip-package", "foo", "--spack-package", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both pip package and spack file' () {
        given:
        def app = new App()
        String[] args = ["--pip-package", "foo", "--spack-file", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both pip package and image' () {
        given:
        def app = new App()
        String[] args = ["--pip-package", "foo", "--image", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both pip packahe and container file' () {
        given:
        def app = new App()
        String[] args = ["--pip-package", "foo", "--containerfile", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }


    def 'should create docker file from conda package' () {
        given:
        def app = new App()
        String[] args = ["--pip", "foo", "--pip", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                FROM mambaorg/micromamba:1.5.5
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\   
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
        and:
        new String(req.condaFile.decodeBase64()) == '''\
        channels:
        - defaults
        dependencies:
        - pip
        - pip:
          - foo
          - bar
        '''.stripIndent(true)
    }

    def 'should create singularity file from conda package' () {
        given:
        def app = new App()
        String[] args = ['--singularity', "--pip", "foo"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.5
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
        and:
        new String(req.condaFile.decodeBase64()) == '''\
                channels:
                - defaults
                dependencies:
                - pip
                - pip:
                  - foo
                '''.stripIndent(true)
    }

}
