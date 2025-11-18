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

import io.seqera.wave.config.CranOpts
import spock.lang.Specification

/**
 * Test for CranHelper
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CranHelperTest extends Specification {

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
