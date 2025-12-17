/*
 * Copyright 2023-2025, Seqera Labs
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
import io.seqera.wave.config.PixiOpts
import picocli.CommandLine
import spock.lang.Specification
/**
 * Test App Pixi prefixed options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AppPixiOptsTest extends Specification {

    def 'should include pixi opts with conda package' () {
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
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: PixiOpts.DEFAULT_PIXI_IMAGE,
                baseImage: PixiOpts.DEFAULT_BASE_IMAGE,
                basePackages: PixiOpts.DEFAULT_PACKAGES
        )
    }

    def 'should include pixi opts with conda file' () {
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
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: PixiOpts.DEFAULT_PIXI_IMAGE,
                baseImage: PixiOpts.DEFAULT_BASE_IMAGE,
                basePackages: PixiOpts.DEFAULT_PACKAGES
        )

        cleanup:
        folder?.deleteDir()
    }

    def 'should include custom pixi build image with conda package' () {
        given:
        def app = new App()
        String[] args = [
                "--conda-package", "foo",
                "--pixi-image", "my/pixi:latest"
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo']
        and:
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: 'my/pixi:latest',
                baseImage: PixiOpts.DEFAULT_BASE_IMAGE,
                basePackages: PixiOpts.DEFAULT_PACKAGES
        )
    }

    def 'should include custom pixi base image with conda package' () {
        given:
        def app = new App()
        String[] args = [
                "--conda-package", "foo",
                "--pixi-base-image", "ubuntu:22.04"
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo']
        and:
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: PixiOpts.DEFAULT_PIXI_IMAGE,
                baseImage: 'ubuntu:22.04',
                basePackages: PixiOpts.DEFAULT_PACKAGES
        )
    }

    def 'should include custom pixi base packages with conda package' () {
        given:
        def app = new App()
        String[] args = [
                "--conda-package", "foo",
                "--pixi-base-packages", "conda-forge::curl"
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo']
        and:
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: PixiOpts.DEFAULT_PIXI_IMAGE,
                baseImage: PixiOpts.DEFAULT_BASE_IMAGE,
                basePackages: 'conda-forge::curl'
        )
    }

    def 'should include pixi run commands with conda package' () {
        given:
        def app = new App()
        String[] args = [
                "--conda-package", "foo",
                "--pixi-run-command", "RUN apt-get update",
                "--pixi-run-command", "RUN apt-get install -y curl"
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo']
        and:
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: PixiOpts.DEFAULT_PIXI_IMAGE,
                baseImage: PixiOpts.DEFAULT_BASE_IMAGE,
                basePackages: PixiOpts.DEFAULT_PACKAGES,
                commands: ['RUN apt-get update', 'RUN apt-get install -y curl']
        )
    }

    def 'should include all pixi options' () {
        given:
        def app = new App()
        String[] args = [
                "--conda-package", "foo",
                "--conda-package", "bar",
                "--pixi-image", "custom/pixi:v1",
                "--pixi-base-image", "debian:12",
                "--pixi-base-packages", "conda-forge::wget",
                "--pixi-run-command", "RUN one",
                "--pixi-run-command", "RUN two"
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.packages.type == PackagesSpec.Type.CONDA
        req.packages.entries == ['foo', 'bar']
        and:
        req.packages.pixiOpts == new PixiOpts(
                pixiImage: 'custom/pixi:v1',
                baseImage: 'debian:12',
                basePackages: 'conda-forge::wget',
                commands: ['RUN one', 'RUN two']
        )
    }
}
