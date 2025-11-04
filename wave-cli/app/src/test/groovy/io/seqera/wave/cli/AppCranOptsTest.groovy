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

package io.seqera.wave.cli

import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.cli.exception.IllegalCliArgumentException
import io.seqera.wave.config.CranOpts
import picocli.CommandLine
import spock.lang.Specification

class AppCranOptsTest extends Specification {

    def 'should fail when passing both cran package and image' () {
        given:
        def app = new App()
        String[] args = ["--cran-package", "dplyr", "--image", "ubuntu:latest"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both cran package and containerfile' () {
        given:
        def app = new App()
        String[] args = ["--cran-package", "dplyr", "--containerfile", "Dockerfile"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both conda and cran packages' () {
        given:
        def app = new App()
        String[] args = ["--conda-package", "samtools", "--cran-package", "dplyr"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message.contains("conda packages and CRAN packages cannot be specified in the same command")
    }

    def 'should fail when passing both conda file and cran packages' () {
        given:
        def app = new App()
        String[] args = ["--conda-file", "env.yml", "--cran-package", "dplyr"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message.contains("conda packages and CRAN packages cannot be specified in the same command")
    }

    def 'should create packages spec from cran package' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran-package', 'dplyr=1.1.0', '--cran-package', 'ggplot2')
        then:
        with(app.packagesSpec()) {
            type == PackagesSpec.Type.CRAN
            entries == ['dplyr=1.1.0', 'ggplot2']
            cranOpts.rImage == CranOpts.DEFAULT_R_IMAGE
            cranOpts.basePackages == CranOpts.DEFAULT_PACKAGES
        }
    }

    def 'should create packages spec from cran package with custom base image' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran-package', 'dplyr', '--cran-base-image', 'rocker/r-ver:4.3.0')
        then:
        with(app.packagesSpec()) {
            type == PackagesSpec.Type.CRAN
            entries == ['dplyr']
            cranOpts.rImage == 'rocker/r-ver:4.3.0'
        }
    }

    def 'should create packages spec from cran package with custom run commands' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran-package', 'dplyr', '--cran-run-command', 'RUN apt-get update')
        then:
        with(app.packagesSpec()) {
            type == PackagesSpec.Type.CRAN
            entries == ['dplyr']
            cranOpts.commands == ['RUN apt-get update']
        }
    }

    def 'should create request with cran package' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran-package', 'dplyr=1.1.0', '--cran-package', 'ggplot2')
        and:
        def req = app.createRequest()
        then:
        with(req) {
            packages.type == PackagesSpec.Type.CRAN
            packages.entries == ['dplyr=1.1.0', 'ggplot2']
            packages.cranOpts.rImage == CranOpts.DEFAULT_R_IMAGE
        }
    }

    def 'should create request with cran package and custom options' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran-package', 'dplyr', '--cran-base-image', 'rocker/tidyverse:4.3.0', '--cran-run-command', 'RUN apt-get update -y')
        and:
        def req = app.createRequest()
        then:
        with(req) {
            packages.type == PackagesSpec.Type.CRAN
            packages.entries == ['dplyr']
            packages.cranOpts.rImage == 'rocker/tidyverse:4.3.0'
            packages.cranOpts.commands == ['RUN apt-get update -y']
        }
    }

    def 'should validate cran package arguments work with short form' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran', 'dplyr', '--cran', 'ggplot2')
        then:
        with(app.packagesSpec()) {
            type == PackagesSpec.Type.CRAN
            entries == ['dplyr', 'ggplot2']
        }
    }

    def 'should validate cran packages are provided in app creation condition' () {
        given:
        def app = new App()

        when:
        new CommandLine(app).parseArgs('--cran-package', 'dplyr')
        and:
        app.validateArgs()
        then:
        noExceptionThrown()
    }
}