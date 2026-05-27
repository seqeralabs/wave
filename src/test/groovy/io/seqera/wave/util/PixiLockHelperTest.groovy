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

class PixiLockHelperTest extends Specification {

    def 'should create docker file from remote lock URL'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: ['https://foo.com/pixi.lock'])

        when:
        def result = PixiLockHelper.containerFile(packages, null, false)

        then:
        result.contains('FROM public.cr.seqera.io/wave/pixi:0.61.0-noble AS build')
        result.contains('curl -fsSL https://foo.com/pixi.lock -o pixi.lock')
        result.contains('pixi install --frozen')
        result.contains('pixi shell-hook > /shell-hook.sh')
        result.contains('FROM ubuntu:24.04 AS final')
        result.contains('COPY --from=build /opt/wave/.pixi/envs/default /opt/wave/.pixi/envs/default')
        result.contains('ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]')
        result.contains('pixi add conda-forge::procps-ng')
        result.contains('>> CONDA_LOCK_START')
        result.contains('<< CONDA_LOCK_END')
    }

    def 'should create singularity file from remote lock URL'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: ['https://foo.com/pixi.lock'])

        when:
        def result = PixiLockHelper.containerFile(packages, null, true)

        then:
        result.contains('BootStrap: docker')
        result.contains('From: public.cr.seqera.io/wave/pixi:0.61.0-noble')
        result.contains('curl -fsSL https://foo.com/pixi.lock -o pixi.lock')
        result.contains('pixi install --frozen')
        result.contains('pixi shell-hook > /shell-hook.sh')
        result.contains('%post')
        result.contains('%environment')
        result.contains('. /shell-hook.sh')
    }

    def 'should throw for non-CONDA type'() {
        given:
        def packages = new PackagesSpec(type: PackagesSpec.Type.CRAN, entries: ['dplyr'])

        when:
        PixiLockHelper.containerFile(packages, null, false)

        then:
        def ex = thrown(BadRequestException)
        ex.message.contains("not supported by 'conda/pixi-lock:v1' build template")
    }

    def 'should use custom pixi opts'() {
        given:
        def opts = new PixiOpts([
                pixiImage: 'ghcr.io/prefix-dev/pixi:0.47.0-jammy-cuda-12.8.1',
                baseImage: 'base/image',
                basePackages: 'foo::one bar::two'
        ])
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: 'ZW52', pixiOpts: opts)

        when:
        def result = PixiLockHelper.containerFile(packages, null, false)

        then:
        result.contains('FROM ghcr.io/prefix-dev/pixi:0.47.0-jammy-cuda-12.8.1 AS build')
        result.contains('COPY conda.yml /opt/wave/pixi.lock')
        result.contains('pixi install --frozen')
        result.contains('FROM base/image AS final')
        result.contains('ghcr.io/prefix-dev/pixi:0.47.0-jammy-cuda-12.8.1')
        result.contains('foo::one bar::two')
    }

    def 'should use custom container image as base image'() {
        given:
        def opts = new PixiOpts([baseImage: 'base/image'])
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: 'ZW52', pixiOpts: opts)

        when:
        def result = PixiLockHelper.containerFile(packages, 'override/base:2.0', false)

        then:
        result.contains('FROM public.cr.seqera.io/wave/pixi:0.61.0-noble AS build')
        result.contains('FROM override/base:2.0 AS final')
        !result.contains('FROM base/image AS final')
    }
}
