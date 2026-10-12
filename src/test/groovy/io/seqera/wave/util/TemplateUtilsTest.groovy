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
                    && pixi add conda-forge::which \\
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

                # set user and environment variables for Python compatibility
                USER root
                ENV USER=root

                # add the env binaries to PATH for when the entrypoint is bypassed (e.g. 'singularity exec' on an OCI-converted image)
                ENV PATH="/opt/wave/.pixi/envs/default/bin:${PATH}"

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
                    && pixi add conda-forge::which \\
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

                # set user and environment variables for Python compatibility
                USER root
                ENV USER=root

                # add the env binaries to PATH for when the entrypoint is bypassed (e.g. 'singularity exec' on an OCI-converted image)
                ENV PATH="/opt/wave/.pixi/envs/default/bin:${PATH}"

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
                    && pixi add conda-forge::which \\
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

                # set user and environment variables for Python compatibility
                USER root
                ENV USER=root

                # add the env binaries to PATH for when the entrypoint is bypassed (e.g. 'singularity exec' on an OCI-converted image)
                ENV PATH="/opt/wave/.pixi/envs/default/bin:${PATH}"

                # set the entrypoint to the shell-hook script (activate the environment and run the command)
                # no more pixi needed in the final container
                ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]

                # Default command for "docker run"
                CMD ["/bin/bash"]
                '''.stripIndent()
    }

    /* *********************************************************************************
     * Micromamba v2 template tests
     *
     * Singularity templates use single-stage builds because Singularity's proot-based
     * builder cannot preserve file permissions when transferring files across stages.
     * Tar extraction and %files from build both fail with permission errors such as:
     *
     *   tar: conda/conda-meta: Cannot change mode to rwxrwxrwx: No such file or directory
     *
     * The conda environment is installed directly in a single stage using the mamba
     * image as the base. Note that {{base_image}} is not used in the Singularity
     * templates — the container uses the mamba image as its base instead.
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
                USER root
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                RUN micromamba install -y -n base conda-forge::which \\
                    && ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which \\
                    && (micromamba install -y -n base -f /tmp/conda.yml > /tmp/mamba.log 2>&1 \\
                    && cat /tmp/mamba.log \\
                    || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                        && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -f /tmp/conda.yml)) \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y \\
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
                USER root
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                RUN micromamba install -y -n base conda-forge::which \\
                    && ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which \\
                    && (micromamba install -y -n base -f /tmp/conda.yml > /tmp/mamba.log 2>&1 \\
                    && cat /tmp/mamba.log \\
                    || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                        && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -f /tmp/conda.yml)) \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y \\
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
                USER root
                # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                RUN \\
                    micromamba install -y -n base conda-forge::which \\
                    && ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which \\
                    && (micromamba install -y -n base -c conda-forge -c bioconda bwa=0.7.15 salmon=1.1.1 > /tmp/mamba.log 2>&1 \\
                    && cat /tmp/mamba.log \\
                    || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                        && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -c conda-forge -c bioconda bwa=0.7.15 salmon=1.1.1)) \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y \\
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
                USER root
                # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                RUN \\
                    micromamba install -y -n base conda-forge::which \\
                    && ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which \\
                    && (micromamba install -y -n base -c conda-forge numpy pandas > /tmp/mamba.log 2>&1 \\
                    && cat /tmp/mamba.log \\
                    || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                        && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -c conda-forge numpy pandas)) \\
                    && micromamba clean -a -y \\
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
     * Pixi v1 template tests (single-stage Singularity builds)
     *
     * Singularity templates use single-stage builds because Singularity's proot-based
     * builder cannot preserve file permissions when transferring files across stages.
     * Tar extraction and %files from build both fail with permission errors such as:
     *
     *   tar: conda/conda-meta: Cannot change mode to rwxrwxrwx: No such file or directory
     *
     * The conda/pixi environment is installed directly in a single stage using the
     * pixi image as the base. Note that {{base_image}} is not used in the Singularity
     * templates — the container uses the pixi image as its base instead.
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
                    pixi add conda-forge::which
                    pixi add conda-forge::procps-ng
                    pixi shell-hook > /shell-hook.sh
                    echo ">> CONDA_LOCK_START"
                    cat /opt/wave/pixi.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    . /shell-hook.sh
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
                    pixi add conda-forge::which
                    pixi add conda-forge::procps-ng
                    pixi shell-hook > /shell-hook.sh
                    echo ">> CONDA_LOCK_START"
                    cat /opt/wave/pixi.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    . /shell-hook.sh
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
                    pixi add conda-forge::which
                    pixi shell-hook > /shell-hook.sh
                    echo ">> CONDA_LOCK_START"
                    cat /opt/wave/pixi.lock
                    echo "<< CONDA_LOCK_END"
                %environment
                    . /shell-hook.sh
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
                    # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                    micromamba install -y -n base conda-forge::which
                    ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which
                    micromamba install -y -n base -f /scratch/conda.yml > /tmp/mamba.log 2>&1 \\
                        && cat /tmp/mamba.log \\
                        || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                            && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -f /scratch/conda.yml)
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba clean -a -y
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
                    # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                    micromamba install -y -n base conda-forge::which
                    ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which
                    micromamba install -y -n base -f /scratch/conda.yml > /tmp/mamba.log 2>&1 \\
                        && cat /tmp/mamba.log \\
                        || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                            && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -f /scratch/conda.yml)
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba clean -a -y
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
                %post
                    # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                    micromamba install -y -n base conda-forge::which
                    ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which
                    micromamba install -y -n base -c conda-forge -c bioconda bwa=0.7.15 salmon=1.1.1 > /tmp/mamba.log 2>&1 \\
                        && cat /tmp/mamba.log \\
                        || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                            && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -c conda-forge -c bioconda bwa=0.7.15 salmon=1.1.1)
                    micromamba install -y -n base conda-forge::procps-ng
                    micromamba clean -a -y
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
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
                %post
                    # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                    micromamba install -y -n base conda-forge::which
                    ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which
                    micromamba install -y -n base -c conda-forge numpy pandas > /tmp/mamba.log 2>&1 \\
                        && cat /tmp/mamba.log \\
                        || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                            && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -c conda-forge numpy pandas)
                    micromamba clean -a -y
                    micromamba env export --name base --explicit > environment.lock
                    echo ">> CONDA_LOCK_START"
                    cat environment.lock
                    echo "<< CONDA_LOCK_END"
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
        !result.contains('Stage: build')
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
        !result.contains('Stage: build')
    }

    /* *********************************************************************************
     * `conda/micromamba:v2-fast` template tests
     *
     * Same build stage as the v2 template, plus the `condasplit` tool (mounted from the
     * layers image) moving the environment into at most 32 layer directories, which the
     * frontend of the same image adds with one `COPY --link` each.
     * *********************************************************************************/

    static final private String LAYERS_IMAGE = 'public.cr.seqera.io/wave/condasplit:v1'


    def 'should create dockerfile using `conda/micromamba:v2-fast` template from conda file' () {
        given:
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng'
        ])

        expect:
        TemplateUtils.condaToDockerFileUsingV2Fast(null, null, CONDA_OPTS, LAYERS_IMAGE) == '''\
                # syntax=public.cr.seqera.io/wave/condasplit:v1
                FROM mambaorg/micromamba:2.1.1 AS build
                USER root
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                # expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
                # the condasplit tool is mounted read-only for this step only and never ends up in the image
                RUN --mount=type=bind,from=public.cr.seqera.io/wave/condasplit:v1,source=/,target=/opt/wave-tools \\
                    micromamba install -y -n base conda-forge::which \\
                    && ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which \\
                    && (micromamba install -y -n base -f /tmp/conda.yml > /tmp/mamba.log 2>&1 \\
                    && cat /tmp/mamba.log \\
                    || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \\
                        && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -f /tmp/conda.yml)) \\
                    && micromamba install -y -n base conda-forge::procps-ng \\
                    && micromamba clean -a -y \\
                    && micromamba env export --name base --explicit > environment.lock \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat environment.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && /opt/wave-tools/condasplit --src /opt/conda --out /layers \\
                        --slots 32 --max-layer-size 500000000 --own-layer-size 50000000 --exclude pkgs

                FROM ubuntu:24.04 AS prod
                ARG MAMBA_ROOT_PREFIX="/opt/conda"
                ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX
                # the condasplit frontend repeats this line for each layer directory: /layers/00, /layers/01, ...
                COPY --link --from=build /layers/NN/ /
                USER root
                ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
                '''.stripIndent()
    }

    def 'should add the remote lock file to the micromamba v2-fast build stage' () {
        given:
        def CONDA_OPTS = new CondaOpts([mambaImage: 'mambaorg/micromamba:2.1.1', baseImage: 'ubuntu:24.04'])

        when:
        def result = TemplateUtils.condaToDockerFileUsingV2Fast(LOCK_URL, ['conda-forge', 'bioconda'], CONDA_OPTS, LAYERS_IMAGE)
        def lines = result.readLines()

        then:
        // the lock file is added to the build stage and installed from the local path
        lines[3] == "# micromamba can't read an explicit lock file from a URL, add it to the build stage"
        lines[4] == "ADD ${LOCK_URL} ${LOCK}".toString()
        lines.count { it.contains("micromamba install -y -n base -c conda-forge -c bioconda -f ${LOCK}") } == 2
        result.count(LOCK_URL) == 1

        where:
        LOCK_URL                                                                    | LOCK
        'https://wave.seqera.io/v1alpha1/builds/bd-edb12e4f0bf02cd3_1/condalock'    | '/tmp/conda-lock/condalock'
        'http://foo.com/env.yml?token=abc'                                          | '/tmp/conda-lock/env.yml'
    }

    def 'should get the lock file name from url' () {
        expect:
        TemplateUtils.lockFileName(LOCK_URL) == EXPECTED

        where:
        LOCK_URL                                                                    | EXPECTED
        'https://wave.seqera.io/v1alpha1/builds/bd-edb12e4f0bf02cd3_1/condalock'    | 'condalock'
        'https://foo.com/some/conda-lock.yml'                                       | 'conda-lock.yml'
        'https://foo.com/env.yaml?token=abc#top'                                    | 'env.yaml'
        'http://foo.com/lock_file.txt'                                              | 'lock_file.txt'
        'https://foo.com/'                                                          | 'conda.lock'
        'https://foo.com'                                                           | 'conda.lock'
        'https://foo.com?x=env.yml'                                                 | 'conda.lock'
        'https://foo.com/..'                                                        | 'conda.lock'
        'https://foo.com/a;b.yml'                                                   | 'conda.lock'
        'https://foo.com/my%20env.yml'                                              | 'conda.lock'
    }

    def 'should render micromamba v2-fast #VARIANT template with commands' () {
        given:
        def CONDA_OPTS = new CondaOpts([
                mambaImage: 'mambaorg/micromamba:2.1.1',
                baseImage: 'ubuntu:24.04',
                basePackages: 'conda-forge::procps-ng',
                commands: ['RUN apt-get update', 'RUN apt-get install -y vim']
        ])

        when:
        def result = TemplateUtils.condaToDockerFileUsingV2Fast(VARIANT=='conda-file' ? null : 'https://foo.com/lock.yml', ['bioconda'], CONDA_OPTS, LAYERS_IMAGE)
        def lines = result.readLines()

        then:
        !result.contains('{{')
        and:
        // the tool image is also the frontend building the image without the empty slots
        lines[0] == '# syntax=public.cr.seqera.io/wave/condasplit:v1'
        and:
        // the tool is mounted in the install step
        lines.count { it.startsWith('RUN --mount=') } == 1
        lines.find { it.startsWith('RUN ') } =='RUN --mount=type=bind,from=public.cr.seqera.io/wave/condasplit:v1,source=/,target=/opt/wave-tools \\'
        and:
        // the tool runs after the conda lock has been printed
        lines.indexOf('    && echo ">> CONDA_LOCK_START" \\') < lines.indexOf('    && echo "<< CONDA_LOCK_END" \\')
        lines.indexOf('    && echo "<< CONDA_LOCK_END" \\') + 1 == lines.indexOf('    && /opt/wave-tools/condasplit --src /opt/conda --out /layers \\')
        lines.indexOf('    && /opt/wave-tools/condasplit --src /opt/conda --out /layers \\') + 1 == lines.indexOf('        --slots 32 --max-layer-size 500000000 --own-layer-size 50000000 --exclude pkgs')
        and:
        // one COPY line in the final stage, the frontend repeats it for each layer directory
        lines.findAll { it.startsWith('COPY --link') } == ['COPY --link --from=build /layers/NN/ /']
        lines.indexOf('COPY --link --from=build /layers/NN/ /') == lines.indexOf('ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX') + 2
        and:
        // custom commands are appended at the end
        result.endsWith('COPY --link --from=build /layers/NN/ /\nUSER root\nENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"\nRUN apt-get update\nRUN apt-get install -y vim\n')

        where:
        VARIANT << ['conda-file', 'lock-file']
    }

    /* *********************************************************************************
     * `conda/pixi:v1-fast` template tests
     *
     * Same build stage as the pixi v1 template, plus the `condasplit` tool (mounted from
     * the layers image) moving the environment into at most 32 layer directories, which
     * the frontend of the same image adds with one `COPY --link` each.
     * *********************************************************************************/

    def 'should create dockerfile using `conda/pixi:v1-fast` template from conda file' () {
        given:
        def PIXI_OPTS = new PixiOpts([basePackages: 'foo::bar'])

        expect:
        TemplateUtils.condaFileToDockerFileUsingPixiV1Fast(PIXI_OPTS, LAYERS_IMAGE) == '''\
                # syntax=public.cr.seqera.io/wave/condasplit:v1
                FROM public.cr.seqera.io/wave/pixi:0.61.0-noble AS build

                COPY conda.yml /opt/wave/conda.yml
                WORKDIR /opt/wave

                # the condasplit tool is mounted read-only for this step only and never ends up in the image
                RUN --mount=type=bind,from=public.cr.seqera.io/wave/condasplit:v1,source=/,target=/opt/wave-tools \\
                    pixi init --import /opt/wave/conda.yml \\
                    && pixi add conda-forge::which \\
                    && pixi add foo::bar \\
                    && pixi shell-hook > /shell-hook.sh \\
                    && echo 'exec "$@"' >> /shell-hook.sh \\
                    && echo ">> CONDA_LOCK_START" \\
                    && cat /opt/wave/pixi.lock \\
                    && echo "<< CONDA_LOCK_END" \\
                    && /opt/wave-tools/condasplit --src /opt/wave/.pixi/envs/default --out /layers \\
                        --slots 32 --max-layer-size 500000000 --own-layer-size 50000000

                FROM ubuntu:24.04 AS final

                # copy the pixi environment in the final container
                # the condasplit frontend repeats this line for each layer directory: /layers/00, /layers/01, ...
                COPY --link --from=build /layers/NN/ /
                COPY --from=build /shell-hook.sh /shell-hook.sh

                # set user and environment variables for Python compatibility
                USER root
                ENV USER=root

                # add the env binaries to PATH for when the entrypoint is bypassed (e.g. 'singularity exec' on an OCI-converted image)
                ENV PATH="/opt/wave/.pixi/envs/default/bin:${PATH}"

                # set the entrypoint to the shell-hook script (activate the environment and run the command)
                # no more pixi needed in the final container
                ENTRYPOINT ["/bin/bash", "/shell-hook.sh"]

                # Default command for "docker run"
                CMD ["/bin/bash"]
                '''.stripIndent()
    }

    def 'should render pixi v1-fast template with custom options and commands' () {
        given:
        def PIXI_OPTS = new PixiOpts([
                pixiImage: 'ghcr.io/prefix-dev/pixi:0.47.0',
                baseImage: 'debian:12',
                basePackages: null,
                commands: ['RUN apt-get update', 'RUN apt-get install -y vim']
        ])

        when:
        def result = TemplateUtils.condaFileToDockerFileUsingPixiV1Fast(PIXI_OPTS, 'my.registry.io/wave/condasplit:v2')
        def lines = result.readLines()

        then:
        !result.contains('{{')
        and:
        // the tool image is also the frontend building the image without the empty slots
        lines[0] == '# syntax=my.registry.io/wave/condasplit:v2'
        lines[1] == 'FROM ghcr.io/prefix-dev/pixi:0.47.0 AS build'
        and:
        // the tool is mounted in the install step and runs after the lock has been printed
        lines.count { it.startsWith('RUN --mount=') } == 1
        lines.find { it.startsWith('RUN ') } == 'RUN --mount=type=bind,from=my.registry.io/wave/condasplit:v2,source=/,target=/opt/wave-tools \\'
        !result.contains('pixi add foo')
        lines.indexOf('    && echo "<< CONDA_LOCK_END" \\') + 1 == lines.indexOf('    && /opt/wave-tools/condasplit --src /opt/wave/.pixi/envs/default --out /layers \\')
        and:
        // one COPY line for the environment, the frontend repeats it for each layer directory
        lines.findAll { it.startsWith('COPY --link') } == ['COPY --link --from=build /layers/NN/ /']
        lines.contains('FROM debian:12 AS final')
        and:
        // custom commands are appended at the end
        result.endsWith('CMD ["/bin/bash"]\nRUN apt-get update\nRUN apt-get install -y vim\n')
    }

}
