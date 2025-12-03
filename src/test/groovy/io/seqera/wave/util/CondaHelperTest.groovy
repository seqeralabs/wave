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
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.exception.BadRequestException

/**
 * Tests for CondaHelper
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CondaHelperTest extends Specification {

    // === tryGetLockFile tests ===

    def 'should return null for null or empty entries'() {
        expect:
        CondaHelper.tryGetLockFile(null) == null
        CondaHelper.tryGetLockFile([]) == null
    }

    def 'should return null when no lock file URI present'() {
        expect:
        CondaHelper.tryGetLockFile(['bwa=0.7.15', 'salmon=1.1.1']) == null
    }

    def 'should detect http lock file URI'() {
        expect:
        CondaHelper.tryGetLockFile(['http://foo.com/lock.yml']) == 'http://foo.com/lock.yml'
    }

    def 'should detect https lock file URI'() {
        expect:
        CondaHelper.tryGetLockFile(['https://foo.com/lock.yml']) == 'https://foo.com/lock.yml'
    }

    def 'should throw exception for multiple lock file URIs'() {
        when:
        CondaHelper.tryGetLockFile(['http://foo.com/lock1.yml', 'http://bar.com/lock2.yml'])

        then:
        thrown(IllegalArgumentException)
    }

    // === containerFile (v1) tests ===

    def 'should create docker file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFile(packages, false)

        then:
        result.contains('FROM mambaorg/micromamba:1.5.10-noble')
        result.contains('COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml')
        result.contains('micromamba install -y -n base -f /tmp/conda.yml')
    }

    def 'should create singularity file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFile(packages, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: mambaorg/micromamba:1.5.10-noble')
        result.contains('micromamba install -y -n base -f /scratch/conda.yml')
    }

    def 'should create docker file with lock file'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def PACKAGES = ['https://foo.com/lock.yml']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFile(packages, false)

        then:
        result.contains('FROM mambaorg/micromamba:1.5.10-noble')
        result.contains('micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/lock.yml')
    }

    def 'should create singularity file with lock file'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def PACKAGES = ['https://foo.com/lock.yml']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFile(packages, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: mambaorg/micromamba:1.5.10-noble')
        result.contains('micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/lock.yml')
    }

    def 'should use custom condaOpts'() {
        given:
        def CHANNELS = ['conda-forge']
        def PACKAGES = ['bwa=0.7.15']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = CondaHelper.containerFile(packages, false)

        then:
        result.contains('micromamba install -y -n base foo::one bar::two')
    }

    // === containerFileV2 (micromamba v2) tests ===

    def 'should create v2 docker file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'bioconda']
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFileV2(packages, null, false)

        then:
        result.contains('FROM mambaorg/micromamba:2.1.1 AS build')
        result.contains('COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml')
        result.contains('micromamba install -y -n base -f /tmp/conda.yml')
        result.contains('FROM ubuntu:24.04 AS prod')
        result.contains('COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"')
    }

    def 'should create v2 singularity file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'bioconda']
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFileV2(packages, null, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: mambaorg/micromamba:2.1.1')
        result.contains('Stage: build')
        result.contains('micromamba install -y -n base -f /tmp/conda.yml')
        result.contains('Bootstrap: docker')
        result.contains('From: ubuntu:24.04')
    }

    def 'should create v2 docker file with lock file'() {
        given:
        def CHANNELS = ['conda-forge', 'bioconda']
        def PACKAGES = ['https://foo.com/lock.yml']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFileV2(packages, null, false)

        then:
        result.contains('FROM mambaorg/micromamba:2.1.1 AS build')
        result.contains('micromamba install -y -n base -c conda-forge -c bioconda -f https://foo.com/lock.yml')
        result.contains('FROM ubuntu:24.04 AS prod')
    }

    def 'should use custom base image in v2'() {
        given:
        def CHANNELS = ['conda-forge']
        def PACKAGES = ['bwa=0.7.15']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = CondaHelper.containerFileV2(packages, 'debian:12', false)

        then:
        result.contains('FROM debian:12 AS prod')
    }

    def 'should use custom condaOpts in v2'() {
        given:
        def CHANNELS = ['conda-forge']
        def PACKAGES = ['bwa=0.7.15']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.0.0',
                baseImage: 'debian:12',
                basePackages: 'foo::one bar::two'
        ])
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = CondaHelper.containerFileV2(packages, null, false)

        then:
        result.contains('FROM mambaorg/micromamba:2.0.0 AS build')
        result.contains('FROM debian:12 AS prod')
        result.contains('micromamba install -y -n base foo::one bar::two')
    }

    def 'should throw exception for non-CONDA package type in v2'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, entries: ['dplyr'])

        when:
        CondaHelper.containerFileV2(packages, null, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("not supported by micromamba/v2 build template")
    }
}
