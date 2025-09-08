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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.ImageNameStrategy
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.request.TokenData

import io.seqera.wave.service.request.ContainerRequest.Type

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
        def layers = [new ContainerLayer(gzipDigest: 'layer1'), new ContainerLayer(gzipDigest: 'layer2')]

        when:
        def result = ContainerHelper.containerFileFromPackages(packages, true, layers)

        then:
        result =='''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %post
                    cd / && tar -xzf /opt/layers/layer-layer1.tar.gz && rm /opt/layers/layer-layer1.tar.gz
                    cd / && tar -xzf /opt/layers/layer-layer2.tar.gz && rm /opt/layers/layer-layer2.tar.gz
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/lock.yml
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

    def 'should create conda docker file with conda lock file'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['https://foo.com/lock.yml']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = ContainerHelper.containerFileFromPackages(packages, false, null)

        then:
        result =='''\
                FROM mambaorg/micromamba:1.5.10-noble
                RUN \\
                    micromamba install -y -n base -c conda-forge -c defaults -f https://foo.com/lock.yml \\
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

    def 'should create conda singularity file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)
        def layers = [new ContainerLayer(gzipDigest: 'layer1'), new ContainerLayer(gzipDigest: 'layer2')]

        when:
        def result = ContainerHelper.containerFileFromPackages(packages, true, layers)

        then:
        result =='''\
                BootStrap: docker
                From: mambaorg/micromamba:1.5.10-noble
                %files
                    {{wave_context_dir}}/conda.yml /scratch/conda.yml
                %post
                    cd / && tar -xzf /opt/layers/layer-layer1.tar.gz && rm /opt/layers/layer-layer1.tar.gz
                    cd / && tar -xzf /opt/layers/layer-layer2.tar.gz && rm /opt/layers/layer-layer2.tar.gz
                    micromamba install -y -n base -f /scratch/conda.yml
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

    def 'should create conda docker file with packages'() {
        given:
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['bwa=0.7.15', 'salmon=1.1.1']
        def packages = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries:  PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)

        when:
        def result = ContainerHelper.containerFileFromPackages(packages, false, null)

        then:
        result =='''\
                FROM mambaorg/micromamba:1.5.10-noble
                COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
                RUN micromamba install -y -n base -f /tmp/conda.yml \\
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

    def 'should validate conda file helper' () {
        given:
        def CONDA = 'this and that'
        def req = new SubmitContainerTokenRequest(condaFile: CONDA.bytes.encodeBase64().toString())
        when:
        def result = ContainerHelper.condaFileFromRequest(req)
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
        def result = ContainerHelper.condaFileFromRequest(req)
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
        def result = ContainerHelper.condaFileFromRequest(req)
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
        def result = ContainerHelper.condaFileFromRequest(req)
        then:
        result == '''\
            channels:
            - defaults
            dependencies:
            - this
            - that
            '''.stripIndent()
    }

    def 'should create response v1' () {
        given:
        def data = ContainerRequest.of(
                containerImage: 'docker.io/some/container',
                buildId:  '123',
                buildNew:  NEW_BUILD )
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

    @Unroll
    def 'should create response v2' () {
        given:
        def data = ContainerRequest.of(
                containerImage:  'docker.io/some/container',
                buildId: BUILD_ID,
                buildNew: NEW_BUILD,
                freeze: IS_FREEZE,
                type: TYPE,
                scanId: "scan-123"
        )
        def token = new TokenData('123abc', Instant.now().plusSeconds(100))
        def target = 'wave.com/this/that'
        and:
        def EXPECTED_IMAGE = 'docker.io/some/container'
        def EXPECTED_SCAN = 'scan-123'

        when:
        def result = ContainerHelper.makeResponseV2(data, token, target)
        then:
        verifyAll(result){
            containerToken == EXPECT_TOKEN
            containerImage == EXPECTED_IMAGE
            targetImage == EXPECT_TARGET
            buildId == BUILD_ID
            cached == EXPECT_CACHE
            freeze == IS_FREEZE
            mirror == EXPECT_MIRROR
            scanId == EXPECTED_SCAN
        }

        where:
        TYPE            | BUILD_ID    | NEW_BUILD   | IS_FREEZE | EXPECT_MIRROR | EXPECT_TOKEN   | EXPECT_TARGET              | EXPECT_CACHE
        Type.Build      | 'build-123' | false       | false     | false         | '123abc'       | 'wave.com/this/that'       | true
        Type.Build      | 'build-123' | true        | false     | false         | '123abc'       | 'wave.com/this/that'       | false
        Type.Build      | 'build-123' | false       | true      | false         | null           | 'docker.io/some/container' | true
        and:
        Type.Build      | 'build-123' | true        | true      | false         | null           | 'docker.io/some/container' | false
        and:
        Type.Mirror     | 'mr-123'    | false       | false     | true          | null           | 'docker.io/some/container' | true
        Type.Mirror     | 'mr-123'    | true        | false     | true          | null           | 'docker.io/some/container' | false
        and:
        Type.Container  | null        | true        | false     | false         | '123abc'       | 'wave.com/this/that'       | null
        Type.Container  | 'bd-123'    | true        | false     | false         | '123abc'       | 'wave.com/this/that'       | false
        Type.Container  | 'bd-123'    | true        | true      | false         | null           | 'docker.io/some/container' | false
    }

    def 'should check if is a conda lock file' () {
        expect:
        ContainerHelper.condaLockFile(null) == null
        ContainerHelper.condaLockFile([]) == null
        ContainerHelper.condaLockFile(['http://foo.com/some/lock']) == 'http://foo.com/some/lock'
        ContainerHelper.condaLockFile(['https://foo.com/some/lock']) == 'https://foo.com/some/lock'

        when:
        ContainerHelper.condaLockFile(['http://foo.com','http://bar.com'])
        then:
        thrown(IllegalArgumentException)
    }

    def 'should patch endpoints' () {
        expect:
        ContainerHelper.patchPlatformEndpoint(ENDPOINT) == EXPECTED

        where:
        ENDPOINT                        | EXPECTED
        null                            | null
        'http://foo.com'                | 'http://foo.com'
        'https://api.tower.nf'          | 'https://api.cloud.seqera.io'
        'https://api.stage-tower.net'   | 'https://api.cloud.stage-seqera.io'
    }

    def 'should find conda name with named recipe' () {
        given:
        def CONDA = '''\
            name: rnaseq-nf
            channels:
              - defaults
              - bioconda
              - conda-forge
            dependencies:
              # Default bismark
              - salmon=1.6.0
              - fastqc=0.11.9
              - multiqc=1.11
            '''.stripIndent(true)

        expect:
        ContainerHelper.guessCondaRecipeName(null) == null
        ContainerHelper.guessCondaRecipeName(CONDA) == new NameVersionPair(['rnaseq-nf'])
        ContainerHelper.guessCondaRecipeName(CONDA,true) == new NameVersionPair(['rnaseq-nf'], [null])
    }

    def 'should find conda name with anonymous recipe' () {
        given:
        def CONDA = '''\
            channels:
              - defaults
              - bioconda
              - conda-forge
            dependencies:
              # Default bismark
              - salmon>=1.6.0
              - fastqc<=0.11.9
              - bioconda::multiqc=1.11
              - samtools
              - bwa>0.1
              - bowtie2<0.2
              - mem==0.3
            '''.stripIndent(true)

        expect:
        ContainerHelper.guessCondaRecipeName(CONDA) == new NameVersionPair(['salmon-1.6.0', 'fastqc-0.11.9', 'multiqc-1.11', 'samtools', 'bwa-0.1', 'bowtie2-0.2', 'mem-0.3'] as Set)
        and:
        ContainerHelper.guessCondaRecipeName(CONDA,true) == new NameVersionPair(['salmon', 'fastqc', 'multiqc', 'samtools', 'bwa', 'bowtie2', 'mem'] as Set, ['1.6.0','0.11.9','1.11', null, '0.1','0.2', '0.3'] as Set)
    }

    def 'should find conda name with pip packages' () {
        given:
        def CONDA = '''\
                channels:
                - bioconda
                - conda-forge
                dependencies:
                - pip
                - pip:
                  - pandas==2.2.2
            '''.stripIndent(true)

        expect:
        ContainerHelper.guessCondaRecipeName(CONDA) == new NameVersionPair(['pip','pandas-2.2.2'] as Set)
        ContainerHelper.guessCondaRecipeName(CONDA,true) == new NameVersionPair(['pip','pandas'] as Set, [null, '2.2.2'] as Set)
    }

    @Unroll
    def 'should normalise tag' () {
        expect:
        ContainerHelper.normaliseTag(TAG,12)  == EXPECTED
        where:
        TAG                     | EXPECTED
        null                    | null
        ''                      | null
        and:
        'foo'                   | 'foo'
        'FOO123'                | 'FOO123'
        'aa-bb_cc.dd'           | 'aa-bb_cc.dd'
        and:
        'one(two)three'         | 'onetwothree'
        '12345_67890_12345'     | '12345_67890'
        '123456789012345_1'     | '123456789012'
        and:
        'aa__'                  | 'aa'
        'aa..--__'              | 'aa'
        '..--__bb'              | 'bb'
        '._-xyz._-'             | 'xyz'
    }

    @Unroll
    def 'should normalise name' () {
        expect:
        ContainerHelper.normaliseName(NAME, 12)  == EXPECTED
        where:
        NAME                    | EXPECTED
        null                    | null
        ''                      | null
        and:
        'foo'                   | 'foo'
        'foo/bar'               | 'foo/bar'
        'FOO123'                | 'foo123'
        'aa-bb_cc.dd'           | 'aa-bb_cc.dd'
        and:
        'one(two)three'         | 'onetwothree'
        '12345_67890_12345'     | '12345_67890'
        '123456789012345_1'     | '123456789012'
        and:
        'aa__'                  | 'aa'
        'aa..--__'              | 'aa'
        '..--__bb'              | 'bb'
        '._-xyz._-'             | 'xyz'
    }

    def 'should make request target' () {
        expect:
        ContainerHelper.makeTargetImage(BuildFormat.DOCKER, 'quay.io/org/name', '12345', null, null)
                == 'quay.io/org/name:12345'
        and:
        ContainerHelper.makeTargetImage(BuildFormat.SINGULARITY, 'quay.io/org/name', '12345', null, null)
                == 'oras://quay.io/org/name:12345'

        and:
        def conda = '''\
        dependencies:
        - salmon=1.2.3
        '''
        ContainerHelper.makeTargetImage(BuildFormat.DOCKER, 'quay.io/org/name', '12345', conda, null)
                == 'quay.io/org/name:salmon-1.2.3--12345'


    }

    @Shared def CONDA1 = '''\
                dependencies:
                    - samtools=1.0
                '''

    @Shared def CONDA2 = '''\
                dependencies:
                    - samtools=1.0
                    - bamtools=2.0
                    - multiqc=1.15
                '''

    @Shared def CONDA3 = '''\
                dependencies:
                    - samtools=1.0
                    - bamtools=2.0
                    - multiqc=1.15
                    - bwa=1.2.3
                    - xx
                    - yy
                    - zz
                '''

    @Shared def PIP1 = '''\
                channels:
                - bioconda
                - conda-forge
                dependencies:
                - pip
                - pip:
                  - pandas==2.2.2
            '''.stripIndent(true)

    @Shared def PIP2 = '''\
                channels:
                - bioconda
                - conda-forge
                dependencies:
                - pip
                - pip:
                  - pandas==2.2.2
                  - numpy=1.0
            '''.stripIndent(true)
    @Shared def GIT1 = '''\
                channels:
                - bioconda
                - conda-forge
                dependencies:
                - pip
                - pip:
                  - "https://github.com/seqeralabs/seqera-kit/archive/dev.zip"
            '''.stripIndent(true)
    @Shared def GIT2 = '''\
                channels:
                - bioconda
                - conda-forge
                dependencies:
                - bwa
                - pip
                - pip:
                  - "https://github.com/seqeralabs/seqera-kit/archive/dev.zip"
                  - "https://github.com/seqeralabs/seqera-kit/archive/dev2.zip"
                  - "https://github.com/seqeralabs/seqera-kit/archive/dev3.zip"
            '''.stripIndent(true)

    @Unroll
    def 'should make request target with name strategy' () {
        expect:
        ContainerHelper.makeTargetImage(
                BuildFormat.valueOf(FORMAT),
                REPO,
                ID,
                CONDA,
                STRATEGY ? ImageNameStrategy.valueOf(STRATEGY) : null) == EXPECTED

        where:
        FORMAT        | REPO              | ID        | CONDA | STRATEGY      | EXPECTED
        'DOCKER'      | 'foo.com/build'   | '123'     | null  | null          | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | null  | 'none'        | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | null  | 'tagPrefix'   | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | null  | 'imageSuffix' | 'foo.com/build:123'
        and:
        'SINGULARITY' | 'foo.com/build'   | '123'     | null  | null          | 'oras://foo.com/build:123'
        'SINGULARITY' | 'foo.com/build'   | '123'     | null  | 'none'        | 'oras://foo.com/build:123'
        'SINGULARITY' | 'foo.com/build'   | '123'     | null  | 'tagPrefix'   | 'oras://foo.com/build:123'
        'SINGULARITY' | 'foo.com/build'   | '123'     | null  | 'imageSuffix' | 'oras://foo.com/build:123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA1| null          | 'foo.com/build:samtools-1.0--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA1| 'none'        | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA1| 'tagPrefix'   | 'foo.com/build:samtools-1.0--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA1| 'imageSuffix' | 'foo.com/build/samtools:1.0--123'
        and:
        'SINGULARITY' | 'foo.com/build'   | '123'     | CONDA1| null          | 'oras://foo.com/build:samtools-1.0--123'
        'SINGULARITY' | 'foo.com/build'   | '123'     | CONDA1| 'none'        | 'oras://foo.com/build:123'
        'SINGULARITY' | 'foo.com/build'   | '123'     | CONDA1| 'tagPrefix'   | 'oras://foo.com/build:samtools-1.0--123'
        'SINGULARITY' | 'foo.com/build'   | '123'     | CONDA1| 'imageSuffix' | 'oras://foo.com/build/samtools:1.0--123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA2| null          | 'foo.com/build:samtools-1.0_bamtools-2.0_multiqc-1.15--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA2| 'none'        | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA2| 'tagPrefix'   | 'foo.com/build:samtools-1.0_bamtools-2.0_multiqc-1.15--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA2| 'imageSuffix' | 'foo.com/build/samtools_bamtools_multiqc:123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA3| null          | 'foo.com/build:samtools-1.0_bamtools-2.0_multiqc-1.15_bwa-1.2.3_pruned--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA3| 'none'        | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA3| 'tagPrefix'   | 'foo.com/build:samtools-1.0_bamtools-2.0_multiqc-1.15_bwa-1.2.3_pruned--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | CONDA3| 'imageSuffix' | 'foo.com/build/samtools_bamtools_multiqc_bwa_pruned:123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP1 | null          | 'foo.com/build:pip_pandas-2.2.2--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP1 | 'none'        | 'foo.com/build:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP1 | 'tagPrefix'   | 'foo.com/build:pip_pandas-2.2.2--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP1 | 'imageSuffix' | 'foo.com/build/pip_pandas:123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP2 | null          | 'foo.com/build:pip_pandas-2.2.2_numpy-1.0--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP2 | 'tagPrefix'   | 'foo.com/build:pip_pandas-2.2.2_numpy-1.0--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP2 | 'imageSuffix' | 'foo.com/build/pip_pandas_numpy:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | PIP2 | 'none'        | 'foo.com/build:123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT1 | null          | 'foo.com/build:pip--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT1 | 'tagPrefix'   | 'foo.com/build:pip--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT1 | 'imageSuffix' | 'foo.com/build/pip:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT1 | 'none'        | 'foo.com/build:123'
        and:
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT2 | null          | 'foo.com/build:bwa_pip--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT2 | 'tagPrefix'   | 'foo.com/build:bwa_pip--123'
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT2 | 'imageSuffix' | 'foo.com/build/bwa_pip:123'
        'DOCKER'      | 'foo.com/build'   | '123'     | GIT2 | 'none'        | 'foo.com/build:123'

    }

    def 'should validate containerfile' () {
        when:
        ContainerHelper.checkContainerSpec(null)
        then:
        noExceptionThrown()

        when:
        ContainerHelper.checkContainerSpec('FROM foo')
        then:
        noExceptionThrown()

        when:
        ContainerHelper.checkContainerSpec('RUN /kaniko/foo')
        then:
        thrown(BadRequestException)

        when:
        ContainerHelper.checkContainerSpec('RUN /.docker/config.json')
        then:
        thrown(BadRequestException)

    }

}
