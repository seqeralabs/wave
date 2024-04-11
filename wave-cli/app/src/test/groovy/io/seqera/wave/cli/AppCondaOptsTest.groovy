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

import java.nio.file.Files

import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.cli.exception.IllegalCliArgumentException
import io.seqera.wave.config.CondaOpts
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

    def 'should fail when the conda file does not exist' () {
        given:
        def app = new App()
        String[] args = ["--conda-file", "foo"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message == "The specified Conda file path cannot be accessed - offending file path: foo"
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
        req.packages.type == PackagesSpec.Type.CONDA
        and:
        new String(req.packages.environment.decodeBase64())  == '''
            name: my-recipe
            dependencies: 
            - one=1.0
            - two:2.0
            '''.stripIndent(true)
        and:
        req.packages.condaOpts == new CondaOpts(mambaImage: CondaOpts.DEFAULT_MAMBA_IMAGE, basePackages: CondaOpts.DEFAULT_PACKAGES)
        req.packages.channels == ['seqera', 'conda-forge', 'bioconda', 'defaults']
        and:
        !req.packages.entries
        and:
        !req.condaFile

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
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo']
        and:
        req.packages.condaOpts == new CondaOpts(mambaImage: CondaOpts.DEFAULT_MAMBA_IMAGE, basePackages: CondaOpts.DEFAULT_PACKAGES)
        req.packages.channels == ['seqera', 'conda-forge', 'bioconda', 'defaults']
        and:
        !req.packages.environment
        and:
        !req.condaFile
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
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['https://host.com/file-lock.yml']
        and:
        req.packages.condaOpts == new CondaOpts(mambaImage: CondaOpts.DEFAULT_MAMBA_IMAGE, basePackages: CondaOpts.DEFAULT_PACKAGES)
        req.packages.channels == ['seqera', 'conda-forge', 'bioconda', 'defaults']
        and:
        !req.packages.environment
        and:
        !req.condaFile
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
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo','bar']
        req.packages.channels == ['alpha','beta']
        and:
        req.packages.condaOpts == new CondaOpts(mambaImage: 'my/mamba:latest', basePackages: CondaOpts.DEFAULT_PACKAGES, commands: ['RUN one','RUN two'])
        and:
        !req.packages.environment
        and:
        !req.condaFile
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
