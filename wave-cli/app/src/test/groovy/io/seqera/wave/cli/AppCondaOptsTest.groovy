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

package io.seqera.wave.cli

import java.nio.file.Files

import io.seqera.wave.cli.exception.IllegalCliArgumentException
import picocli.CommandLine
import spock.lang.Specification
/**
 * Test App Conda prefixed options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AppCondaOptsTest extends Specification {

    def 'should fail when passing both conda file and packages' () {
        given:
        def app = new App()
        String[] args = ["--conda-file", "foo", "--conda-package", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both conda file and image' () {
        given:
        def app = new App()
        String[] args = ["--conda-file", "foo", "--image", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both conda file and container file' () {
        given:
        def app = new App()
        String[] args = ["--conda-file", "foo", "--containerfile", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both conda package and image' () {
        given:
        def app = new App()
        String[] args = ["--conda-package", "foo", "--image", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both conda package and container file' () {
        given:
        def app = new App()
        String[] args = ["--conda-package", "foo", "--containerfile", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should create docker file from conda file' () {
        given:
        def CONDA_RECIPE = '''
            name: my-recipe
            dependencies: 
            - one=1.0
            - two:2.0
            '''.stripIndent(true)
        and:
        def folder = Files.createTempDirectory('test')
        def condaFile = folder.resolve('conda.yml');
        condaFile.text = CONDA_RECIPE
        and:
        def app = new App()
        String[] args = ["--conda-file", condaFile.toString()]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                FROM mambaorg/micromamba:1.5.1
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y
                USER root
                '''.stripIndent()
        and:
        new String(req.condaFile.decodeBase64()) == CONDA_RECIPE

        cleanup:
        folder?.deleteDir()
    }


    def 'should create docker file from conda package' () {
        given:
        def app = new App()
        String[] args = ["--conda-package", "foo"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                FROM mambaorg/micromamba:1.5.1
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\   
                    && micromamba clean -a -y
                USER root
                '''.stripIndent()
        and:
        new String(req.condaFile.decodeBase64()) == '''\
        channels:
        - seqera
        - bioconda
        - conda-forge
        - defaults
        dependencies:
        - foo
        '''.stripIndent(true)
    }

    def 'should create docker env from conda lock file' () {
        given:
        def app = new App()
        String[] args = ["--conda-package", "https://host.com/file-lock.yml"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                FROM mambaorg/micromamba:1.5.1
                RUN \\
                    micromamba install -y -n base -c seqera -c bioconda -c conda-forge -c defaults -f https://host.com/file-lock.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y
                USER root
                '''.stripIndent()
        and:
        req.condaFile == null
    }

    def 'should create docker file from conda package and custom options' () {
        given:
        def app = new App()
        String[] args = [
                "--conda-package", "foo",
                "--conda-package", "bar",
                "--conda-base-image", "my/mamba:latest",
                "--conda-channels", "alpha,beta",
                "--conda-run-command", "RUN one",
                "--conda-run-command", "RUN two",

        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                FROM my/mamba:latest
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y
                USER root
                RUN one
                RUN two
                '''.stripIndent()

        and:
        new String(req.condaFile.decodeBase64()) == '''\
            channels:
            - alpha
            - beta
            dependencies:
            - foo
            - bar
            '''.stripIndent(true)
    }


    def 'should create singularity file from conda file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def condaFile = folder.resolve('conda.yml');
        condaFile.text = 'MY CONDA FILE'
        and:
        def app = new App()
        String[] args = ['--singularity', "--conda-file", condaFile.toString()]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.1
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
        new String(req.condaFile.decodeBase64()) == 'MY CONDA FILE'

        cleanup:
        folder?.deleteDir()
    }


    def 'should create singularity file from conda package' () {
        given:
        def app = new App()
        String[] args = ['--singularity', "--conda-package", "foo"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.1
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
                - seqera
                - bioconda
                - conda-forge
                - defaults
                dependencies:
                - foo
                '''.stripIndent(true)
    }

    def 'should create singularity file from conda package and custom options' () {
        given:
        def app = new App()
        String[] args = [
                '--singularity',
                "--conda-package", "foo",
                "--conda-package", "bar",
                "--conda-base-image", "my/mamba:latest",
                "--conda-channels", "alpha,beta",
                "--conda-run-command", "RUN one",
                "--conda-run-command", "RUN two",
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
                BootStrap: docker
                From: my/mamba:latest
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                %post
                    RUN one
                    RUN two
                '''.stripIndent(true)

        and:
        new String(req.condaFile.decodeBase64()) == '''\
                channels:
                - alpha
                - beta
                dependencies:
                - foo
                - bar
                '''.stripIndent(true)
    }

    def 'should create singularity file from conda lock file' () {
        given:
        def app = new App()
        String[] args = ["--conda-package", "https://host.com/file-lock.yml", '--singularity']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()) == '''\
            BootStrap: docker
            From: mambaorg/micromamba:1.5.1
            %post
                micromamba install -y -n base -c seqera -c bioconda -c conda-forge -c defaults -f https://host.com/file-lock.yml
                micromamba install -y -n base conda-forge::procps-ng
                micromamba clean -a -y
            %environment
                export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
        and:
        req.condaFile == null
    }


    def 'should get conda lock file' () {
        expect:
        new App(condaPackages: ['https://foo.com/lock.yml'])
                .condaLock() == 'https://foo.com/lock.yml'

        and:
        new App(condaPackages: ['foo', 'bar'])
                .condaLock() == null

        and:
        new App(condaPackages: null)
                .condaLock() == null

        when:
        new App(condaPackages: ['foo', 'http://foo.com']) .condaLock()
        then:
        thrown(IllegalCliArgumentException)
    }


    def 'should get conda channels' () {
        expect:
        new App(condaChannels: null)
                .condaChannels() == null

        new App(condaChannels: 'foo , bar')
                .condaChannels() == ['foo','bar']

        new App(condaChannels: 'foo bar')
                .condaChannels() == ['foo','bar']
    }
}
