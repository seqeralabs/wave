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
import io.seqera.wave.config.PixiOpts
import io.seqera.wave.exception.BadRequestException

/**
 * Tests for PixiHelper
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PixiHelperTest extends Specification {

    def 'should create docker file with packages'() {
        given:
        def CHANNELS = ['conda-forge']
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = PixiHelper.containerFile(packages, null, false)

        then:
        result.contains('FROM ghcr.io/prefix-dev/pixi:latest AS build')
        result.contains('COPY conda.yml /opt/wave/conda.yml')
        result.contains('pixi init --import /opt/wave/conda.yml')
        result.contains('pixi add conda-forge::procps-ng')
        result.contains('pixi shell-hook > /shell-hook.sh')
        result.contains('FROM ubuntu:24.04 AS final')
        result.contains('COPY --from=build /opt/wave/.pixi/envs/default /opt/wave/.pixi/envs/default')
        result.contains('ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]')
    }

    def 'should create singularity file with packages'() {
        given:
        def CHANNELS = ['conda-forge']
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = PixiHelper.containerFile(packages, null, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: ghcr.io/prefix-dev/pixi:latest')
        result.contains('Stage: build')
        result.contains('pixi init --import /scratch/conda.yml')
        result.contains('pixi shell-hook > /shell-hook.sh')
        result.contains('Bootstrap: docker')
        result.contains('From: ubuntu:24.04')
        result.contains('Stage: final')
    }

    def 'should use custom base image'() {
        given:
        def CHANNELS = ['conda-forge']
        def PACKAGES = ['bwa=0.7.15']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS)

        when:
        def result = PixiHelper.containerFile(packages, 'base/custom:1.0', false)

        then:
        result.contains('FROM base/custom:1.0 AS final')
    }

    def 'should use custom pixiOpts'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def PIXI_OPTS = new PixiOpts(
                basePackages: 'foo::one bar::two',
                baseImage: 'base/image',
                pixiImage: 'ghcr.io/prefix-dev/pixi:0.47.0-jammy-cuda-12.8.1')
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(
                type: PackagesSpec.Type.CONDA,
                entries: PACKAGES,
                channels: CHANNELS,
                pixiOpts: PIXI_OPTS)

        when:
        def result = PixiHelper.containerFile(packages, null, false)

        then:
        result.contains('FROM ghcr.io/prefix-dev/pixi:0.47.0-jammy-cuda-12.8.1 AS build')
        result.contains('pixi add foo::one bar::two')
        result.contains('FROM base/image AS final')
    }

    def 'should throw exception for non-CONDA package type'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, entries: ['dplyr'])

        when:
        PixiHelper.containerFile(packages, null, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("not supported by 'pixi/v1' build template")
    }

    def 'should throw exception for lock file'() {
        given:
        def packages = new PackagesSpec(
                type: PackagesSpec.Type.CONDA,
                entries: ['https://foo.com/lock.yml'],
                channels: ['conda-forge'])

        when:
        PixiHelper.containerFile(packages, null, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("Conda lock file is not supported by 'pixi/v1' template")
    }
}
