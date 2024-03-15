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
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.config.SpackOpts

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
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, packages:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)
        def request = new SubmitContainerTokenRequest(format: 'sif', packages:packages)

        when:
        def result = ContainerHelper.createContainerFile(request)

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
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, packages:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)
        def request = new SubmitContainerTokenRequest(format:  'docker', packages:packages)

        when:
        def result = ContainerHelper.createContainerFile(request)

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
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, packages:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)
        def request = new SubmitContainerTokenRequest(format: 'sif', packages:packages)

        when:
        def result = ContainerHelper.createContainerFile(request)

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
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, packages:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)
        def request = new SubmitContainerTokenRequest(format: 'docker', packages:packages)

        when:
        def result = ContainerHelper.createContainerFile(request)

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
        def request = new SubmitContainerTokenRequest(format: 'sif', packages:packages)

        when:
        def result = ContainerHelper.createContainerFile(request)

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

    def 'should create conda docker file'() {
        given:
        def SPACK_OPTS = new SpackOpts([
                basePackages: 'foo bar',
                commands: ['run','--this','--that']
        ])
        def packages = new PackagesSpec(type: PackagesSpec.Type.SPACK, spackOpts: SPACK_OPTS)
        def request = new SubmitContainerTokenRequest(format: 'docker', packages:packages)

        when:
        def result = ContainerHelper.createContainerFile(request)

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
}
