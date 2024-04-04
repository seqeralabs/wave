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

package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.config.SpackOpts
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.token.TokenData
/**
 * Container helper methods
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ContainerHelperTest extends Specification {

    def 'should create conda singularity file with conda lock file'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['https://foo.com/lock.yml']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = ContainerHelper.createContainerFile(packages, true)

        then:
        result =='''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.5
                %post
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/lock.yml
                    micromamba install -y -n base foo::one bar::two
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()

    }

    def 'should create conda docker file with conda lock file'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['https://foo.com/lock.yml']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = ContainerHelper.createContainerFile(packages, false)

        then:
        result =='''\
                FROM mambaorg/micromamba:1.5.5
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/lock.yml \\
                    && micromamba install -y -n base foo::one bar::two \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()

    }

    def 'should create conda singularity file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = ContainerHelper.createContainerFile(packages, true)

        then:
        result =='''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.5
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base foo::one bar::two
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                    '''.stripIndent()
    }

    def 'should create conda docker file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = ContainerHelper.createContainerFile(packages, false)

        then:
        result =='''\
                FROM mambaorg/micromamba:1.5.5
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base foo::one bar::two \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create spack singularity file'() {
        given:
        def SPACK_OPTS = new SpackOpts([
                basePackages: 'foo bar',
                commands: ['run','--this','--that']
        ])
        def packages = new PackagesSpec(type: PackagesSpec.Type.SPACK, spackOpts: SPACK_OPTS)

        when:
        def result = ContainerHelper.createContainerFile(packages, true)

        then:
        result == '''\
                Bootstrap: docker
                From: {{spack_runner_image}}
                stage: final
                
                %files from build
                    /opt/spack-env /opt/spack-env
                    /opt/software /opt/software
                    /opt/._view /opt/._view
                    /opt/spack-env/z10_spack_environment.sh /.singularity.d/env/91-environment.sh
                
                %post
                    run
                    --this
                    --that
                    '''.stripIndent()
    }

    def 'should create spack docker file'() {
        given:
        def SPACK_OPTS = new SpackOpts([
                basePackages: 'foo bar',
                commands: ['run','--this','--that']
        ])
        def packages = new PackagesSpec(type: PackagesSpec.Type.SPACK, spackOpts: SPACK_OPTS)

        when:
        def result = ContainerHelper.createContainerFile(packages, false)

        then:
        result == '''\
                # Runner image
                FROM {{spack_runner_image}}
                
                COPY --from=builder /opt/spack-env /opt/spack-env
                COPY --from=builder /opt/software /opt/software
                COPY --from=builder /opt/._view /opt/._view
                
                # Entrypoint for Singularity
                RUN mkdir -p /.singularity.d/env && \\
                    cp -p /opt/spack-env/z10_spack_environment.sh /.singularity.d/env/91-environment.sh
                # Entrypoint for Docker
                RUN echo "#!/usr/bin/env bash\\n\\nset -ef -o pipefail\\nsource /opt/spack-env/z10_spack_environment.sh\\nexec \\"\\$@\\"" \\
                    >/opt/spack-env/spack_docker_entrypoint.sh && chmod a+x /opt/spack-env/spack_docker_entrypoint.sh
                
                run
                --this
                --that
                
                ENTRYPOINT [ "/opt/spack-env/spack_docker_entrypoint.sh" ]
                CMD [ "/bin/bash" ]
                '''.stripIndent()
    }


    def 'should validate conda file helper' () {
        given:
        def CONDA = 'this and that'
        def req = new SubmitContainerTokenRequest(condaFile: CONDA.bytes.encodeBase64().toString())
        when:
        def result = ContainerHelper.condaFile0(req)
        then:
        result == CONDA
    }

    def 'should validate conda env helper' () {
        given:
        def CONDA = '''\
            channels:
            - conda-forge
            - defaults
            dependencies:
            - bwa=0.7.15
            - salmon=1.1.1
            '''.stripIndent()
        and:
        def req = new SubmitContainerTokenRequest(packages: new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: CONDA.bytes.encodeBase64().toString()))

        when:
        def result = ContainerHelper.condaFile0(req)
        then:
        result == CONDA
    }

    def 'should validate conda env and channels helper' () {
        given:
        def CONDA = '''\
            channels:
            - aa
            dependencies:
            - bwa=0.7.15
            - salmon=1.1.1
            '''.stripIndent()
        and:
        def spec = new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: CONDA.bytes.encodeBase64().toString(), channels: ['xx', 'yy'])
        def req = new SubmitContainerTokenRequest(packages: spec)

        when:
        def result = ContainerHelper.condaFile0(req)
        then:
        result == '''\
            channels:
            - aa
            - xx
            - yy
            dependencies:
            - bwa=0.7.15
            - salmon=1.1.1
            '''.stripIndent()
    }

    def 'should validate conda packages helper' () {
        given:
        def spec = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: ['this', 'that'], channels: ['defaults'])
        def req = new SubmitContainerTokenRequest(packages: spec)

        when:
        def result = ContainerHelper.condaFile0(req)
        then:
        result == '''\
            channels:
            - defaults
            dependencies:
            - this
            - that
            '''.stripIndent()
    }

    def 'should validate spack file helper' () {
        given:
        def SPACK = 'this and that'
        def req = new SubmitContainerTokenRequest(spackFile: SPACK.bytes.encodeBase64().toString())
        when:
        def result = ContainerHelper.spackFile0(req)
        then:
        result == SPACK
    }

    def 'should validate spack env file helper' () {
        given:
        def SPACK = '''\
                spack:
                  specs: [bwa@0.7.15, salmon@1.1.1]
                  concretizer: {unify: true, reuse: false}
                '''.stripIndent(true)
        and:
        def spec = new PackagesSpec(type: PackagesSpec.Type.SPACK, environment: SPACK.bytes.encodeBase64().toString())
        def req = new SubmitContainerTokenRequest(packages: spec)

        when:
        def result = ContainerHelper.spackFile0(req)
        then:
        result == SPACK
    }

    def 'should validate spack env packages helper' () {
        given:
        def spec = new PackagesSpec(type: PackagesSpec.Type.SPACK, entries: ['foo', 'bar'])
        def req = new SubmitContainerTokenRequest(packages: spec)

        when:
        def result = ContainerHelper.spackFile0(req)
        then:
        result == '''\
            spack:
              specs: [foo, bar]
              concretizer: {unify: true, reuse: false}
            '''.stripIndent(true)
    }

    def 'should create response v1' () {
        given:
        def data = new ContainerRequestData(null,
                'docker.io/some/container',
                null,
                null,
                null,
                null,
                '123',
                NEW_BUILD
        )
        def token = new TokenData('123abc', Instant.now().plusSeconds(100))
        def target = 'wave.com/this/that'
        when:
        def result = ContainerHelper.makeResponseV1(data, token, target)
        then:
        result.containerToken == '123abc'
        result.targetImage == 'wave.com/this/that'
        result.expiration == token.expiration
        result.containerImage == 'docker.io/some/container'
        result.buildId == EXPECTED_BUILD_ID
        result.cached == null
        result.freeze == null

        where: 
        NEW_BUILD   | EXPECTED_BUILD_ID
        false       | null
        true        | '123'
    }
}
