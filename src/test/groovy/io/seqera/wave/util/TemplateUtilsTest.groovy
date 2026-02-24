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

import io.seqera.wave.config.CondaOpts
import io.seqera.wave.config.PixiOpts
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TemplateUtilsTest extends Specification {

    def 'should create dockerfile content from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::bar'])

        expect:
        TemplateUtils.condaFileToDockerFile(CONDA_OPTS)== '''\
                FROM mambaorg/micromamba:1.5.10-noble
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base foo::bar \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile content from conda file and base packages' () {

        expect:
        TemplateUtils.condaFileToDockerFile(new CondaOpts([:]))== '''\
                FROM mambaorg/micromamba:1.5.10-noble
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }


    def 'should create dockerfile content from conda package' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'defaults']
        expect:
        TemplateUtils.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile with base packages' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        TemplateUtils.condaPackagesToDockerFile(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base foo::one bar::two \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile content with custom channels' () {
        given:
        def CHANNELS = 'foo,bar'.tokenize(',')
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        TemplateUtils.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c foo -c bar bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile content with custom conda config' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = [mambaImage:'my-base:123', commands: ['USER my-user', 'RUN apt-get update -y && apt-get install -y nano']]
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        TemplateUtils.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts(CONDA_OPTS)) == '''\
                FROM my-base:123
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                USER my-user
                RUN apt-get update -y && apt-get install -y nano
                '''.stripIndent()
    }


    def 'should create dockerfile content with remote conda lock' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def OPTS = [mambaImage:'my-base:123', commands: ['USER my-user', 'RUN apt-get update -y && apt-get install -y procps']]
        def PACKAGES = 'https://foo.com/some/conda-lock.yml'

        expect:
        TemplateUtils.condaPackagesToDockerFile(PACKAGES, CHANNELS, new CondaOpts(OPTS)) == '''\
                FROM my-base:123
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/some/conda-lock.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && micromamba clean -a -y
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                USER my-user
                RUN apt-get update -y && apt-get install -y procps
                '''.stripIndent()
    }


    /* *********************************************************************************
     * conda packages to singularity tests
     * *********************************************************************************/

    def 'should create singularity content from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::bar=1.0'])

        expect:
        TemplateUtils.condaFileToSingularityFile(CONDA_OPTS)== '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base foo::bar=1.0
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity content from conda file and base packages' () {

        expect:
        TemplateUtils.condaFileToSingularityFile(new CondaOpts([:]))== '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }


    def 'should create singularity content from conda package' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'defaults']
        expect:
        TemplateUtils.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity with base packages' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        TemplateUtils.condaPackagesToSingularityFile(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base foo::one bar::two
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity content with custom channels' () {
        given:
        def CHANNELS = 'foo,bar'.tokenize(',')
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        TemplateUtils.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts([:])) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    micromamba install -y -n base -c foo -c bar bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularity content with custom conda config' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = [mambaImage:'my-base:123', commands: ['install --this --that', 'apt-get update -y && apt-get install -y nano']]
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'

        expect:
        TemplateUtils.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts(CONDA_OPTS)) == '''\
                BootStrap: docker
                From: my-base:123
                %post
                    micromamba install -y -n base -c conda-forge -c defaults bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                %post
                    install --this --that
                    apt-get update -y && apt-get install -y nano
                '''.stripIndent()
    }


    def 'should create singularity content with remote conda lock' () {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def OPTS = [mambaImage:'my-base:123', commands: ['apt-get update -y && apt-get install -y procps']]
        def PACKAGES = 'https://foo.com/some/conda-lock.yml'

        expect:
        TemplateUtils.condaPackagesToSingularityFile(PACKAGES, CHANNELS, new CondaOpts(OPTS)) == '''\
                BootStrap: docker
                From: my-base:123
                %post
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/some/conda-lock.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                    micromamba clean -a -y
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                %post
                    apt-get update -y && apt-get install -y procps
                '''.stripIndent()
    }

    def 'should create dockerfile content from conda file using pixi' () {
        given:
        def PIXI_OPTS = new PixiOpts([basePackages: 'foo::bar'])

        expect:
        TemplateUtils.condaFileToDockerFileUsingPixi(PIXI_OPTS)== '''\
                FROM public.cr.seqera.io/wave/pixi:0.61.0-noble AS build

                COPY conda.yml /opt/wave/conda.yml
                WORKDIR /opt/wave

                RUN pixi init --import /opt/wave/conda.yml \\
                    && pixi add foo::bar \\
                    && pixi shell-hook > /shell-hook.sh \\
                    && echo 'exec "$@"' >> /shell-hook.sh \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat /opt/wave/pixi.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM ubuntu:24.04 AS final

                # copy the pixi environment in the final container
                COPY --from=build /opt/wave/.pixi/envs/default /opt/wave/.pixi/envs/default
                COPY --from=build /shell-hook.sh /shell-hook.sh

                # set the entrypoint to the shell-hook script (activate the environment and run the command)
                # no more pixi needed in the final container
                ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]

                # Default command for "docker run"
                CMD ["/bin/bash"]
                '''.stripIndent()
    }

    def 'should create dockerfile content from conda file using pixi with default options' () {
        expect:
        TemplateUtils.condaFileToDockerFileUsingPixi(new PixiOpts([:])) == '''\
                FROM public.cr.seqera.io/wave/pixi:0.61.0-noble AS build

                COPY conda.yml /opt/wave/conda.yml
                WORKDIR /opt/wave

                RUN pixi init --import /opt/wave/conda.yml \\
                    && pixi add conda-forge::procps-ng \\
                    && pixi shell-hook > /shell-hook.sh \\
                    && echo 'exec "$@"' >> /shell-hook.sh \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat /opt/wave/pixi.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM ubuntu:24.04 AS final

                # copy the pixi environment in the final container
                COPY --from=build /opt/wave/.pixi/envs/default /opt/wave/.pixi/envs/default
                COPY --from=build /shell-hook.sh /shell-hook.sh

                # set the entrypoint to the shell-hook script (activate the environment and run the command)
                # no more pixi needed in the final container
                ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]

                # Default command for "docker run"
                CMD ["/bin/bash"]
                '''.stripIndent()
    }

    def 'should create dockerfile content from conda file using pixi with custom base image' () {
        given:
        def PIXI_OPTS = new PixiOpts([baseImage: 'debian:12'])

        expect:
        TemplateUtils.condaFileToDockerFileUsingPixi(PIXI_OPTS)== '''\
                FROM public.cr.seqera.io/wave/pixi:0.61.0-noble AS build

                COPY conda.yml /opt/wave/conda.yml
                WORKDIR /opt/wave

                RUN pixi init --import /opt/wave/conda.yml \\
                    && pixi add conda-forge::procps-ng \\
                    && pixi shell-hook > /shell-hook.sh \\
                    && echo 'exec "$@"' >> /shell-hook.sh \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat /opt/wave/pixi.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM debian:12 AS final

                # copy the pixi environment in the final container
                COPY --from=build /opt/wave/.pixi/envs/default /opt/wave/.pixi/envs/default
                COPY --from=build /shell-hook.sh /shell-hook.sh

                # set the entrypoint to the shell-hook script (activate the environment and run the command)
                # no more pixi needed in the final container
                ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]

                # Default command for "docker run"
                CMD ["/bin/bash"]
                '''.stripIndent()
    }

    /* *********************************************************************************
     * Micromamba v2 template tests (multi-stage builds)
     * *********************************************************************************/

    def 'should create dockerfile using micromamba v2 template from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng'
        ])

        expect:
        TemplateUtils.condaFileToDockerFileUsingV2(CONDA_OPTS) == '''\
                FROM mambaorg/micromamba:2.1.1 AS build
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM ubuntu:24.04 AS prod
                ARG MAMBA_ROOT_PREFIX="/opt/conda"
                ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX
                COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile using micromamba v2 template with default options' () {
        expect:
        TemplateUtils.condaFileToDockerFileUsingV2(new CondaOpts([:])) == '''\
                FROM mambaorg/micromamba:1.5.10-noble AS build
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM ubuntu:24.04 AS prod
                ARG MAMBA_ROOT_PREFIX="/opt/conda"
                ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX
                COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile using micromamba v2 template from packages' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'bioconda']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng'
        ])

        expect:
        TemplateUtils.condaPackagesToDockerFileUsingV2(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                FROM mambaorg/micromamba:2.1.1 AS build
                RUN \\
                    micromamba install -y -n base -c conda-forge -c bioconda bwa=0.7.15 salmon=1.1.1 \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM ubuntu:24.04 AS prod
                ARG MAMBA_ROOT_PREFIX="/opt/conda"
                ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX
                COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile using micromamba v2 template with custom base image' () {
        given:
        def PACKAGES = 'numpy pandas'
        def CHANNELS = ['conda-forge']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'debian:12',
                basePackages: null
        ])

        expect:
        TemplateUtils.condaPackagesToDockerFileUsingV2(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                FROM mambaorg/micromamba:2.1.1 AS build
                RUN \\
                    micromamba install -y -n base -c conda-forge numpy pandas \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END"

                FROM debian:12 AS prod
                ARG MAMBA_ROOT_PREFIX="/opt/conda"
                ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX
                COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile using micromamba v2 template with commands' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'bioconda']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng',
                commands: ['RUN apt-get update', 'RUN apt-get install -y vim']
        ])

        when:
        def result = TemplateUtils.condaPackagesToDockerFileUsingV2(PACKAGES, CHANNELS, CONDA_OPTS)

        then:
        result.contains('FROM mambaorg/micromamba:2.1.1 AS build')
        result.contains('FROM ubuntu:24.04 AS prod')
        result.contains('RUN apt-get update')
        result.contains('RUN apt-get install -y vim')
    }

    def 'should create dockerfile using micromamba v2 template with remote lock file' () {
        given:
        def PACKAGES = 'https://foo.com/some/conda-lock.yml'
        def CHANNELS = ['conda-forge']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04'
        ])

        when:
        def result = TemplateUtils.condaPackagesToDockerFileUsingV2(PACKAGES, CHANNELS, CONDA_OPTS)

        then:
        result.contains('-f https://foo.com/some/conda-lock.yml')
        result.contains('FROM mambaorg/micromamba:2.1.1 AS build')
        result.contains('FROM ubuntu:24.04 AS prod')
    }

    /* *********************************************************************************
     * Pixi v1 template tests (multi-stage builds)
     * *********************************************************************************/

    def 'should create singularityfile using pixi v1 template' () {
        given:
        def PIXI_OPTS = new PixiOpts([
                pixiImage: 'ghcr.io/prefix-dev/pixi:latest',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng'
        ])

        expect:
        TemplateUtils.condaFileToSingularityFileUsingPixi(PIXI_OPTS) == '''\
                BootStrap: docker
                From: ghcr.io/prefix-dev/pixi:latest
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    mkdir /opt/wave && cd /opt/wave
                    pixi init --import /scratch/conda.yml
                    pixi add conda-forge::procps-ng
                    pixi shell-hook > /shell-hook.sh
                    echo 'exec "$@"' >> /shell-hook.sh
                    echo ">> CONDA_LOCK_START"
                    cat /opt/wave/pixi.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularityfile using pixi v1 template with default options' () {
        expect:
        TemplateUtils.condaFileToSingularityFileUsingPixi(new PixiOpts([:])) == '''\
                BootStrap: docker
                From: public.cr.seqera.io/wave/pixi:0.61.0-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    mkdir /opt/wave && cd /opt/wave
                    pixi init --import /scratch/conda.yml
                    pixi add conda-forge::procps-ng
                    pixi shell-hook > /shell-hook.sh
                    echo 'exec "$@"' >> /shell-hook.sh
                    echo ">> CONDA_LOCK_START"
                    cat /opt/wave/pixi.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create singularityfile using pixi v1 template with custom images' () {
        given:
        def PIXI_OPTS = new PixiOpts([
                pixiImage: 'ghcr.io/prefix-dev/pixi:0.35.0',
                baseImage: 'debian:12',
                basePackages: null
        ])

        expect:
        TemplateUtils.condaFileToSingularityFileUsingPixi(PIXI_OPTS) == '''\
                BootStrap: docker
                From: ghcr.io/prefix-dev/pixi:0.35.0
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    mkdir /opt/wave && cd /opt/wave
                    pixi init --import /scratch/conda.yml
                    pixi shell-hook > /shell-hook.sh
                    echo 'exec "$@"' >> /shell-hook.sh
                    echo ">> CONDA_LOCK_START"
                    cat /opt/wave/pixi.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should create dockerfile using pixi v1 template with custom commands' () {
        given:
        def PIXI_OPTS = new PixiOpts([
                basePackages: 'conda-forge::procps-ng',
                commands: ['RUN apt-get update', 'RUN apt-get install -y curl']
        ])

        when:
        def result = TemplateUtils.condaFileToDockerFileUsingPixi(PIXI_OPTS)

        then:
        result.contains('pixi add conda-forge::procps-ng')
        result.contains('RUN apt-get update')
        result.contains('RUN apt-get install -y curl')
    }

    def 'should create singularityfile using pixi v1 template with custom commands' () {
        given:
        def PIXI_OPTS = new PixiOpts([
                basePackages: 'conda-forge::bash',
                commands: ['apt-get update', 'apt-get install -y nano']
        ])

        when:
        def result = TemplateUtils.condaFileToSingularityFileUsingPixi(PIXI_OPTS)

        then:
        result.contains('pixi add conda-forge::bash')
        result.contains('%post')
        result.contains('apt-get update')
        result.contains('apt-get install -y nano')
    }

    def 'should create singularityfile using micromamba v2 template from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng'
        ])

        expect:
        TemplateUtils.condaFileToSingularityFileV2(CONDA_OPTS) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:2.1.1
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    export MAMBA_ROOT_PREFIX=/opt/conda
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()

    }

    def 'should create singularityfile using micromamba v2 template with default options' () {
        expect:
        TemplateUtils.condaFileToSingularityFileV2(new CondaOpts([:])) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    micromamba install -y -n base -f /scratch/conda.yml
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    export MAMBA_ROOT_PREFIX=/opt/conda
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                    '''.stripIndent()
    }

    def 'should create singularityfile using micromamba v2 template from packages' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'bioconda']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng'
        ])

        expect:
        TemplateUtils.condaPackagesToSingularityFileV2(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:2.1.1
                Stage: build
                %post
                    micromamba install -y -n base -c conda-forge -c bioconda bwa=0.7.15 salmon=1.1.1
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"

                Bootstrap: docker
                From: ubuntu:24.04
                Stage: final
                %files from build
                    /opt/conda /opt/conda
                %environment
                    export MAMBA_ROOT_PREFIX=/opt/conda
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                    '''.stripIndent()
    }

    def 'should create singularityfile using micromamba v2 template with custom base image' () {
        given:
        def PACKAGES = 'numpy pandas'
        def CHANNELS = ['conda-forge']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'debian:12',
                basePackages: null
        ])

        expect:
        TemplateUtils.condaPackagesToSingularityFileV2(PACKAGES, CHANNELS, CONDA_OPTS) == '''\
                BootStrap: docker
                From: mambaorg/micromamba:2.1.1
                Stage: build
                %post
                    micromamba install -y -n base -c conda-forge numpy pandas
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"

                Bootstrap: docker
                From: debian:12
                Stage: final
                %files from build
                    /opt/conda /opt/conda
                %environment
                    export MAMBA_ROOT_PREFIX=/opt/conda
                    export PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                    '''.stripIndent()
    }

    def 'should create singularityfile using micromamba v2 template with commands' () {
        given:
        def PACKAGES = 'bwa=0.7.15 salmon=1.1.1'
        def CHANNELS = ['conda-forge', 'bioconda']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng',
                commands: ['apt-get update', 'apt-get install -y vim']
        ])

        when:
        def result = TemplateUtils.condaPackagesToSingularityFileV2(PACKAGES, CHANNELS, CONDA_OPTS)

        then:
        result.contains('From: mambaorg/micromamba:2.1.1')
        result.contains('From: ubuntu:24.04')
        result.contains('%post')
        result.contains('apt-get update')
        result.contains('apt-get install -y vim')
    }

    def 'should create singularityfile using micromamba v2 template with remote lock file' () {
        given:
        def PACKAGES = 'https://foo.com/some/conda-lock.yml'
        def CHANNELS = ['conda-forge']
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04'
        ])

        when:
        def result = TemplateUtils.condaPackagesToSingularityFileV2(PACKAGES, CHANNELS, CONDA_OPTS)

        then:
        result.contains('-f https://foo.com/some/conda-lock.yml')
        result.contains('From: mambaorg/micromamba:2.1.1')
        result.contains('From: ubuntu:24.04')
    }

}
