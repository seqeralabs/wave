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
import io.seqera.wave.config.AptOpts
import io.seqera.wave.exception.BadRequestException

/**
 * Tests for AptHelper
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AptHelperTest extends Specification {

    // === T009 - Test for package list to Dockerfile ===

    def 'should create docker file with package list via containerFile'() {
        given:
        def PACKAGES = ['curl', 'wget', 'git']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        result.contains('FROM ubuntu:24.04')
        result.contains('DEBIAN_FRONTEND=noninteractive')
        result.contains('apt-get install -y --no-install-recommends')
        result.contains('ca-certificates curl wget git')
        result.contains('apt-get clean')
        result.contains('rm -rf /var/lib/apt/lists/*')
    }

    // === T010 - Test for package list to Singularity file ===

    def 'should create singularity file with package list via containerFile'() {
        given:
        def PACKAGES = ['samtools', 'bcftools']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES)

        when:
        def result = AptHelper.containerFile(packages, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: ubuntu:24.04')
        result.contains('export DEBIAN_FRONTEND=noninteractive')
        result.contains('apt-get install -y --no-install-recommends')
        result.contains('ca-certificates samtools bcftools')
        result.contains('apt-get clean')
        result.contains('rm -rf /var/lib/apt/lists/*')
    }

    // === T011 - Test for environment file parsing ===

    def 'should parse environment file content'() {
        expect:
        AptHelper.parseEnvironmentFile(CONTENT) == EXPECTED

        where:
        CONTENT                              | EXPECTED
        null                                 | []
        ''                                   | []
        'curl'                               | ['curl']
        'curl\nwget'                         | ['curl', 'wget']
        'curl\nwget\ngit'                    | ['curl', 'wget', 'git']
        '# comment\ncurl'                    | ['curl']
        'curl\n# comment\nwget'              | ['curl', 'wget']
        '  curl  \n  wget  '                 | ['curl', 'wget']
        'curl\n\nwget'                       | ['curl', 'wget']
        '# System utilities\ncurl\nwget\n\n# Build tools\nbuild-essential' | ['curl', 'wget', 'build-essential']
    }

    def 'should create docker file from environment file via containerFile'() {
        given:
        def envContent = '''\
            # System utilities
            curl
            wget
            git
            '''.stripIndent()
        def encoded = Base64.encoder.encodeToString(envContent.bytes)
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, environment: encoded)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        result.contains('FROM ubuntu:24.04')
        result.contains('apt-get install -y --no-install-recommends')
        result.contains('ca-certificates curl wget git')
    }

    // === T012 - Test for version-pinned packages ===

    def 'should handle version-pinned packages'() {
        given:
        def PACKAGES = ['nginx=1.18.0-0ubuntu1', 'curl']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        result.contains('nginx=1.18.0-0ubuntu1 curl')
    }

    // === Additional tests for containerFile ===

    def 'should use default AptOpts when not provided'() {
        given:
        def PACKAGES = ['curl', 'wget']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        // Should use default base image from AptOpts
        result.contains('FROM ubuntu:24.04')
        // Should use default base packages
        result.contains('ca-certificates')
    }

    def 'should throw exception for non-APT package type'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: ['bwa=0.7.15'])

        when:
        AptHelper.containerFile(packages, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("not supported by 'apt/debian:v1' build template")
    }

    def 'should throw exception when no packages provided'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT)

        when:
        AptHelper.containerFile(packages, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("require either 'entries' or 'environment' field")
    }

    def 'should throw exception for empty package list'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: [])

        when:
        AptHelper.containerFile(packages, false)

        then:
        def ex = thrown(BadRequestException)
        // Empty list is treated as no entries in Groovy (falsy), so we get this error
        ex.message.contains("require either 'entries' or 'environment' field")
    }

    // === Low-level helper tests ===

    def 'should create complete dockerfile for apt packages'() {
        given:
        def APT_OPTS = new AptOpts([baseImage: 'ubuntu:22.04', basePackages: 'ca-certificates'])
        def PACKAGES = 'curl wget git'

        when:
        def result = AptHelper.aptPackagesToDockerFile(PACKAGES, APT_OPTS)

        then:
        result == '''\
            FROM ubuntu:22.04
            ENV DEBIAN_FRONTEND=noninteractive
            RUN apt-get update \\
                && apt-get install -y --no-install-recommends ca-certificates curl wget git \\
                && apt-get clean \\
                && rm -rf /var/lib/apt/lists/*
            '''.stripIndent(true)
    }

    def 'should create complete singularity file for apt packages'() {
        given:
        def APT_OPTS = new AptOpts([baseImage: 'ubuntu:22.04', basePackages: 'ca-certificates'])
        def PACKAGES = 'curl wget'

        when:
        def result = AptHelper.aptPackagesToSingularityFile(PACKAGES, APT_OPTS)

        then:
        result == '''\
            BootStrap: docker
            From: ubuntu:22.04
            %post
                export DEBIAN_FRONTEND=noninteractive
                apt-get update
                apt-get install -y --no-install-recommends ca-certificates curl wget
                apt-get clean
                rm -rf /var/lib/apt/lists/*
            '''.stripIndent(true)
    }

    // === US2 Tests - Custom options ===

    def 'should use custom baseImage in AptOpts'() {
        given:
        def APT_OPTS = new AptOpts([baseImage: 'debian:bookworm'])
        def PACKAGES = ['curl']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES, aptOpts: APT_OPTS)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        result.contains('FROM debian:bookworm')
        !result.contains('FROM ubuntu:24.04')
    }

    def 'should use custom basePackages in AptOpts'() {
        given:
        def APT_OPTS = new AptOpts([basePackages: 'ca-certificates locales'])
        def PACKAGES = ['curl']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES, aptOpts: APT_OPTS)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        result.contains('ca-certificates locales curl')
    }

    def 'should add custom commands to Dockerfile'() {
        given:
        def APT_OPTS = new AptOpts([commands: ['echo "setup complete"', 'locale-gen en_US.UTF-8']])
        def PACKAGES = ['curl']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES, aptOpts: APT_OPTS)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        result.contains('RUN echo "setup complete"')
        result.contains('RUN locale-gen en_US.UTF-8')
    }

    def 'should add custom commands to Singularity file'() {
        given:
        def APT_OPTS = new AptOpts([commands: ['echo "setup complete"', 'locale-gen en_US.UTF-8']])
        def PACKAGES = ['curl']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES, aptOpts: APT_OPTS)

        when:
        def result = AptHelper.containerFile(packages, true)

        then:
        result.contains('%post')
        result.contains('    echo "setup complete"')
        result.contains('    locale-gen en_US.UTF-8')
    }

    def 'should handle null/empty AptOpts with defaults'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: ['curl'])

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        // Default base image
        result.contains('FROM ubuntu:24.04')
        // Default base packages
        result.contains('ca-certificates')
    }

    def 'should handle AptOpts with empty basePackages'() {
        given:
        def APT_OPTS = new AptOpts()
        APT_OPTS.basePackages = ''
        def PACKAGES = ['curl']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES, aptOpts: APT_OPTS)

        when:
        def result = AptHelper.containerFile(packages, false)

        then:
        // With empty basePackages, we get a leading space before the target package
        result.contains('apt-get install -y --no-install-recommends')
        result.contains('curl')
    }

    def 'should handle multiple packages in complete output'() {
        given:
        def APT_OPTS = new AptOpts([baseImage: 'ubuntu:24.04', basePackages: 'ca-certificates'])
        def PACKAGES = ['curl', 'wget', 'git', 'build-essential']
        def packages = new PackagesSpec(type: PackagesSpec.Type.APT, entries: PACKAGES, aptOpts: APT_OPTS)

        when:
        def dockerResult = AptHelper.containerFile(packages, false)
        def singularityResult = AptHelper.containerFile(packages, true)

        then:
        dockerResult == '''\
            FROM ubuntu:24.04
            ENV DEBIAN_FRONTEND=noninteractive
            RUN apt-get update \\
                && apt-get install -y --no-install-recommends ca-certificates curl wget git build-essential \\
                && apt-get clean \\
                && rm -rf /var/lib/apt/lists/*
            '''.stripIndent(true)

        singularityResult == '''\
            BootStrap: docker
            From: ubuntu:24.04
            %post
                export DEBIAN_FRONTEND=noninteractive
                apt-get update
                apt-get install -y --no-install-recommends ca-certificates curl wget git build-essential
                apt-get clean
                rm -rf /var/lib/apt/lists/*
            '''.stripIndent(true)
    }
}
