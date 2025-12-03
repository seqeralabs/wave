/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.config.CranOpts
import io.seqera.wave.exception.BadRequestException

/**
 * Tests for CranHelper
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CranHelperTest extends Specification {

    // === containerFile (PackagesSpec) tests ===

    def 'should create docker file with packages via containerFile'() {
        given:
        def REPOSITORIES = ['cran', 'bioconductor']
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1', basePackages: 'littler r-cran-docopt'])
        def PACKAGES = ['dplyr', 'ggplot2', 'bioc::GenomicRanges']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, entries: PACKAGES, channels: REPOSITORIES, cranOpts: CRAN_OPTS)

        when:
        def result = CranHelper.containerFile(packages, false)

        then:
        result.contains('FROM rocker/r-ver:4.4.1')
        result.contains('install2.r')
        result.contains("'dplyr' 'ggplot2' BiocManager::install('GenomicRanges')")
        result.contains('R_LIBS_USER="/usr/local/lib/R/site-library"')
    }

    def 'should create singularity file with packages via containerFile'() {
        given:
        def REPOSITORIES = ['cran']
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1'])
        def PACKAGES = ['tidyverse', 'data.table']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, entries: PACKAGES, channels: REPOSITORIES, cranOpts: CRAN_OPTS)

        when:
        def result = CranHelper.containerFile(packages, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: rocker/r-ver:4.4.1')
        result.contains('install2.r')
        result.contains("'tidyverse' 'data.table'")
        result.contains('export R_LIBS_USER="/usr/local/lib/R/site-library"')
    }

    def 'should create docker file without packages (file mode) via containerFile'() {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1'])
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, cranOpts: CRAN_OPTS)

        when:
        def result = CranHelper.containerFile(packages, false)

        then:
        result.contains('FROM rocker/r-ver:4.4.1')
        result.contains('COPY --from=wave_context_dir . .')
        result.contains('renv.lock')
        result.contains('install.R')
        result.contains('R_LIBS_USER="/usr/local/lib/R/site-library"')
    }

    def 'should create singularity file without packages (file mode) via containerFile'() {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1', basePackages: 'build-essential'])
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, cranOpts: CRAN_OPTS)

        when:
        def result = CranHelper.containerFile(packages, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: rocker/r-ver:4.4.1')
        result.contains('%files')
        result.contains('/opt/wave_context_dir')
        result.contains('build-essential')
        result.contains('renv.lock')
        result.contains('export R_LIBS_USER="/usr/local/lib/R/site-library"')
    }

    def 'should use default CranOpts when not provided'() {
        given:
        def PACKAGES = ['dplyr', 'ggplot2']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, entries: PACKAGES)

        when:
        def result = CranHelper.containerFile(packages, false)

        then:
        // Should use default R image from CranOpts
        result.contains('FROM rocker/')
        result.contains('install2.r')
    }

    def 'should throw exception for non-CRAN package type'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: ['bwa=0.7.15'])

        when:
        CranHelper.containerFile(packages, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("not supported by 'cran/v1' build template")
    }

    // === Low-level helper tests ===

    def 'should trim a string' () {
        expect:
        CranHelper.trim0(STR) == EXPECTED

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
    }

    def 'should convert cran packages to list' () {
        expect:
        CranHelper.cranPackagesToList(PACKAGES) == EXPECTED

        where:
        PACKAGES         | EXPECTED
        null             | null
        ""               | null
        "foo"            | ['foo']
        "foo bar"        | ['foo', 'bar']
        " foo   bar   "  | ['foo', 'bar']
        "foo 'bar' baz"  | ['foo', 'bar', 'baz']
        "foo \"bar\" baz"| ['foo', 'bar', 'baz']
    }

    def 'should create dockerfile for cran packages - complete test' () {
        given:
        def REPOSITORIES = ['cran', 'bioconductor']
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.3.0', basePackages: 'build-essential'])
        def PACKAGES = 'dplyr ggplot2 bioc::GenomicRanges'

        when:
        def result = CranHelper.cranPackagesToDockerFile(PACKAGES, REPOSITORIES, CRAN_OPTS)

        then:
        result == '''\
            FROM rocker/r-ver:4.3.0
            RUN \\
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/')); options(BioC_mirror = 'https://bioconductor.org')" \\
                && apt-get update && apt-get install -y build-essential \\
                && install2.r 'dplyr' 'ggplot2' BiocManager::install('GenomicRanges') \\
                && rm -rf /tmp/downloaded_packages/ /tmp/*.rds \\
                && rm -rf /var/lib/apt/lists/*
            USER root
            ENV R_LIBS_USER="/usr/local/lib/R/site-library"
            '''
                .stripIndent(true)
    }

    def 'should create singularity file for cran packages - complete test' () {
        given:
        def REPOSITORIES = ['cran']
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1', basePackages: 'littler r-cran-docopt'])
        def PACKAGES = 'tidyverse data.table'

        when:
        def result = CranHelper.cranPackagesToSingularityFile(PACKAGES, REPOSITORIES, CRAN_OPTS)

        then:
        result == '''\
            BootStrap: docker
            From: rocker/r-ver:4.4.1
            %post
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/'))"
                apt-get update && apt-get install -y littler r-cran-docopt
                install2.r 'tidyverse' 'data.table'
                rm -rf /tmp/downloaded_packages/ /tmp/*.rds
                rm -rf /var/lib/apt/lists/*
            %environment
                export R_LIBS_USER="/usr/local/lib/R/site-library"
                '''
                .stripIndent(true)
    }

    def 'should create dockerfile for cran file' () {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1'])

        when:
        def result = CranHelper.cranFileToDockerFile(CRAN_OPTS)

        then:
        result.contains('FROM rocker/r-ver:4.4.1')
        result.contains('COPY --from=wave_context_dir . .')
        result.contains('renv.lock')
        result.contains('install.R')
        result.contains('R_LIBS_USER="/usr/local/lib/R/site-library"')
    }

    def 'should create singularity file for cran file' () {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1', basePackages: 'build-essential'])

        when:
        def result = CranHelper.cranFileToSingularityFile(CRAN_OPTS)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: rocker/r-ver:4.4.1')
        result.contains('%files')
        result.contains('/opt/wave_context_dir')
        result.contains('build-essential')
        result.contains('renv.lock')
        result.contains('export R_LIBS_USER="/usr/local/lib/R/site-library"')
    }

    def 'should handle empty packages' () {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1'])
        def PACKAGES = ''

        when:
        def result = CranHelper.cranPackagesToDockerFile(PACKAGES, [], CRAN_OPTS)

        then:
        result.contains('FROM rocker/r-ver:4.4.1')
        result.contains('install2.r')
    }

    def 'should handle remote renv.lock files' () {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1'])
        def PACKAGES = 'https://example.com/renv.lock'

        when:
        def result = CranHelper.cranPackagesToDockerFile(PACKAGES, [], CRAN_OPTS)

        then:
        result.contains('https://example.com/renv.lock')
    }

    def 'should add custom commands' () {
        given:
        def CRAN_OPTS = new CranOpts([
            rImage: 'rocker/r-ver:4.4.1',
            commands: ['RUN echo "custom command"', 'RUN echo "another command"']
        ])
        def PACKAGES = 'dplyr'

        when:
        def dockerResult = CranHelper.cranPackagesToDockerFile(PACKAGES, [], CRAN_OPTS)
        def singularityResult = CranHelper.cranPackagesToSingularityFile(PACKAGES, [], CRAN_OPTS)

        then:
        dockerResult.contains('RUN echo "custom command"')
        dockerResult.contains('RUN echo "another command"')
        singularityResult.contains('    RUN echo "custom command"')
        singularityResult.contains('    RUN echo "another command"')
    }

    def 'should handle empty repositories list - complete dockerfile test' () {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1'])
        def PACKAGES = 'shiny'

        when:
        def dockerResult = CranHelper.cranPackagesToDockerFile(PACKAGES, [], CRAN_OPTS)
        def singularityResult = CranHelper.cranPackagesToSingularityFile(PACKAGES, [], CRAN_OPTS)

        then:
        dockerResult == '''\
            FROM rocker/r-ver:4.4.1
            RUN \\
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/'))" \\
                && apt-get update && apt-get install -y littler r-cran-docopt \\
                && install2.r 'shiny' \\
                && rm -rf /tmp/downloaded_packages/ /tmp/*.rds \\
                && rm -rf /var/lib/apt/lists/*
            USER root
            ENV R_LIBS_USER="/usr/local/lib/R/site-library"
            '''.stripIndent(true)

        singularityResult == '''\
            BootStrap: docker
            From: rocker/r-ver:4.4.1
            %post
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/'))"
                apt-get update && apt-get install -y littler r-cran-docopt
                install2.r 'shiny'
                rm -rf /tmp/downloaded_packages/ /tmp/*.rds
                rm -rf /var/lib/apt/lists/*
            %environment
                export R_LIBS_USER="/usr/local/lib/R/site-library"
                '''
                .stripIndent(true)
    }

    def 'should handle null base packages - complete test' () {
        given:
        def CRAN_OPTS = new CranOpts()
        CRAN_OPTS.rImage = 'rocker/r-ver:4.4.1'
        CRAN_OPTS.basePackages = null
        def PACKAGES = 'dplyr'

        when:
        def dockerResult = CranHelper.cranPackagesToDockerFile(PACKAGES, ['cran'], CRAN_OPTS)
        def singularityResult = CranHelper.cranPackagesToSingularityFile(PACKAGES, ['cran'], CRAN_OPTS)

        then:
        dockerResult == '''\
            FROM rocker/r-ver:4.4.1
            RUN \\
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/'))" \\
                && install2.r 'dplyr' \\
                && rm -rf /tmp/downloaded_packages/ /tmp/*.rds \\
                && rm -rf /var/lib/apt/lists/*
            USER root
            ENV R_LIBS_USER="/usr/local/lib/R/site-library"
            '''
                .stripIndent(true)

        singularityResult == '''\
            BootStrap: docker
            From: rocker/r-ver:4.4.1
            %post
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/'))"
                install2.r 'dplyr'
                rm -rf /tmp/downloaded_packages/ /tmp/*.rds
                rm -rf /var/lib/apt/lists/*
            %environment
                export R_LIBS_USER="/usr/local/lib/R/site-library"
                '''
                .stripIndent(true)
    }

    def 'should handle empty base packages string - complete test' () {
        given:
        def CRAN_OPTS = new CranOpts([rImage: 'rocker/r-ver:4.4.1', basePackages: ''])
        def PACKAGES = 'ggplot2'

        when:
        def result = CranHelper.cranPackagesToDockerFile(PACKAGES, [], CRAN_OPTS)

        then:
        result == '''\
            FROM rocker/r-ver:4.4.1
            RUN \\
                R -e "options(repos = c(CRAN = 'https://cloud.r-project.org/'))" \\
                && install2.r 'ggplot2' \\
                && rm -rf /tmp/downloaded_packages/ /tmp/*.rds \\
                && rm -rf /var/lib/apt/lists/*
            USER root
            ENV R_LIBS_USER="/usr/local/lib/R/site-library"
            '''
                .stripIndent(true)
    }
}
