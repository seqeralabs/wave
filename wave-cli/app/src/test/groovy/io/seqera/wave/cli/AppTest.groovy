/*
 * Copyright 2023, Seqera Labs
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

package io.seqera.wave.cli

import java.nio.file.Files
import java.time.Duration
import java.time.Instant

import io.seqera.wave.api.ContainerStatus
import io.seqera.wave.api.ContainerStatusResponse
import io.seqera.wave.api.ImageNameStrategy
import io.seqera.wave.api.ScanLevel
import io.seqera.wave.api.ScanMode
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.cli.exception.BadClientResponseException
import io.seqera.wave.cli.exception.IllegalCliArgumentException
import io.seqera.wave.cli.model.ContainerInspectResponseEx
import io.seqera.wave.cli.model.SubmitContainerTokenResponseEx
import io.seqera.wave.cli.util.DurationConverter
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.util.TarUtils
import picocli.CommandLine
import spock.lang.Specification
import spock.lang.Unroll

class AppTest extends Specification {


    def "test valid no entrypoint"() {
        given:
        def app = new App()
        String[] args = []
        def cli = new CommandLine(app)

        when:
        cli.parseArgs(args)
        then:
        app.@entrypoint == null

        when:
        def config = app.prepareConfig()
        then:
        config == null
    }

    def 'should dump response to yaml' () {
        given:
        def app = new App()
        String[] args = ["--output", "yaml"]
        and:
        def resp = new SubmitContainerTokenResponse(
                containerToken: "12345",
                targetImage: 'docker.io/some/repo',
                containerImage: 'docker.io/some/container',
                expiration: Instant.ofEpochMilli(1691839913),
                buildId: '98765',
                cached: true
        )

        when:
        new CommandLine(app).parseArgs(args)
        def result = app.dumpOutput(resp)
        then:
        result == '''\
            buildId: '98765'
            cached: true
            containerImage: docker.io/some/container
            containerToken: '12345'
            expiration: '1970-01-20T13:57:19.913Z'
            targetImage: docker.io/some/repo
            '''.stripIndent(true)
    }

    def 'should dump response with status to yaml' () {
        given:
        def app = new App()
        String[] args = ["--output", "yaml"]
        and:
        def resp = new SubmitContainerTokenResponse(
                containerToken: "12345",
                targetImage: 'docker.io/some/repo',
                containerImage: 'docker.io/some/container',
                expiration: Instant.ofEpochMilli(1691839913),
                buildId: '98765',
                cached: true
        )
        def status = new ContainerStatusResponse(
                "12345",
                ContainerStatus.DONE,
                "98765",
                null,
                "scan-1234",
                [MEDIUM:1, HIGH:2],
                true,
                "All ok",
                "http://foo.com",
                Instant.now(),
                Duration.ofMinutes(1)
        )

        when:
        new CommandLine(app).parseArgs(args)
        def result = app.dumpOutput(new SubmitContainerTokenResponseEx(resp, status))
        then:
        result == '''\
            buildId: '98765'
            cached: true
            containerImage: docker.io/some/container
            containerToken: '12345'
            detailsUri: http://foo.com
            duration: PT1M
            expiration: '1970-01-20T13:57:19.913Z'
            reason: All ok
            status: DONE
            succeeded: true
            targetImage: docker.io/some/repo
            vulnerabilities:
              MEDIUM: 1
              HIGH: 2
            '''.stripIndent(true)
    }

    def 'should throw exception on failure' (){
        given:
        def app = new App()
        String[] args = []
        and:
        def resp = new SubmitContainerTokenResponse(
                containerToken: "12345",
                targetImage: 'docker.io/some/repo',
                containerImage: 'docker.io/some/container',
                expiration: Instant.ofEpochMilli(1691839913),
                buildId: '98765',
                cached: false
        )
        def status = new ContainerStatusResponse(
                "12345",
                ContainerStatus.DONE,
                "98765",
                null,
                "scan-1234",
                [MEDIUM:1, HIGH:2],
                false,
                "Something went wrong",
                "http://foo.com/bar/1234",
                Instant.now(),
                Duration.ofMinutes(1)
        )

        when:
        new CommandLine(app).parseArgs(args)
        app.dumpOutput(new SubmitContainerTokenResponseEx(resp, status))
        then:
        def e = thrown(BadClientResponseException)
        e.message == '''\
            Container provisioning did not complete successfully
            - Reason: Something went wrong
            - Find out more here: http://foo.com/bar/1234\
            '''.stripIndent()
    }

    def 'should dump response to json' () {
        given:
        def app = new App()
        String[] args = ["--output", "json"]
        and:
        def resp = new SubmitContainerTokenResponse(
                containerToken: "12345",
                targetImage: 'docker.io/some/repo',
                containerImage: 'docker.io/some/container',
                expiration: Instant.ofEpochMilli(1691839913),
                buildId: '98765'
        )

        when:
        new CommandLine(app).parseArgs(args)
        def result = app.dumpOutput(resp)
        then:
        result == '{"buildId":"98765","containerImage":"docker.io/some/container","containerToken":"12345","expiration":"1970-01-20T13:57:19.913Z","targetImage":"docker.io/some/repo"}'
    }

    def 'should dump inspect to json' () {
        given:
        def app = new App()
        String[] args = ["--output", "json"]
        and:
        def resp = new ContainerInspectResponseEx( new ContainerSpec('docker.io', 'https://docker.io', 'busybox', 'latest', 'sha:12345', null, null) )

        when:
        new CommandLine(app).parseArgs(args)
        def result = app.dumpOutput(resp)
        then:
        result == '{"container":{"digest":"sha:12345","hostName":"https://docker.io","imageName":"busybox","reference":"latest","registry":"docker.io"}}'
    }

    def 'should dump inspect to yaml' () {
        given:
        def app = new App()
        String[] args = ["--output", "yaml"]
        and:
        def resp = new ContainerInspectResponseEx( new ContainerSpec('docker.io', 'https://docker.io', 'busybox', 'latest', 'sha:12345', null, null) )

        when:
        new CommandLine(app).parseArgs(args)
        def result = app.dumpOutput(resp)
        then:
        result == '''\
            container:
              digest: sha:12345
              hostName: https://docker.io
              imageName: busybox
              reference: latest
              registry: docker.io
            '''.stripIndent()
    }

    def 'should prepare context' () {
        given:
        def folder = Files.createTempDirectory('test')
        def source = Files.createDirectory(folder.resolve('source'))
        def target = Files.createDirectory(folder.resolve('target'))
        folder.resolve('source/.dockerignore').text = '''\
        **.txt
        !README.txt
        '''
        and:
        source.resolve('hola.txt').text = 'Hola'
        source.resolve('ciao.txt').text = 'Ciao'
        source.resolve('script.sh').text = 'echo Hello'
        source.resolve('README.txt').text = 'Do this and that'
        and:
        def app = new App()
        String[] args = ["--context", source.toString()]

        when:
        new CommandLine(app).parseArgs(args)
        def layer = app.prepareContext()
        then:
        noExceptionThrown()

        when:
        def gzip = layer.location.replace('data:','').decodeBase64()
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), target)
        then:
        target.resolve('script.sh').text == 'echo Hello'
        target.resolve('README.txt').text == 'Do this and that'
        and:
        !Files.exists(target.resolve('hola.txt'))
        !Files.exists(target.resolve('ciao.txt'))

        cleanup:
        folder?.deleteDir()
    }

    def 'should enable dry run mode' () {
        given:
        def app = new App()
        String[] args = ["--dry-run"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.dryRun
    }

    def 'should set scan mode' () {
        given:
        def app = new App()
        String[] args = ["--scan-mode", 'async']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.scanMode == ScanMode.async
        req.scanLevels == null
    }

    def 'should set scan levels' () {
        given:
        def app = new App()
        String[] args = ["--scan-level", 'LOW', "--scan-level", 'MEDIUM']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        req.scanMode == null
        req.scanLevels == List.of(ScanLevel.LOW, ScanLevel.MEDIUM)
    }

    def 'should not allow dry-run and await' () {
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest","--dry-run", '--await']

        when:
        def cli = new CommandLine(app)
        cli.registerConverter(Duration.class, new DurationConverter())
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message == 'Options --dry-run and --await conflicts each other'
    }

    @Unroll
    def 'should allow platform option' () {
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest","--platform", PLATFORM]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        app.@platform == PLATFORM

        where:
        PLATFORM        || _
        'amd64'         || _
        'x86_64'        || _
        'arm64'         || _
        'linux/amd64'   || _
        'linux/x86_64'  || _
        'linux/arm64'   || _
    }

    @Unroll
    def 'should fail with unsupported platform' () {
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest","--platform", 'foo']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message == "Unsupported container platform: 'foo'"
    }

    def 'should allow platform amd64 with singularity' () {
        given:
        def app = new App()
        String[] args = [ '--singularity', "--platform", 'linux/amd64',  '-i', 'ubuntu', '--freeze', '--build-repo', 'docker.io/foo',  '--tower-token', 'xyz']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()

        then:
        noExceptionThrown()
    }

    def 'should allow platform arm64 with singularity' () {
        given:
        def app = new App()
        String[] args = [ '--singularity', "--platform", 'linux/arm64',  '-i', 'ubuntu', '--freeze', '--build-repo', 'docker.io/foo',  '--tower-token', 'xyz']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()

        then:
        noExceptionThrown()
        and:
        app.@platform == 'linux/arm64'
        app.@image == 'ubuntu'
        app.@singularity
        app.@freeze
        app.@buildRepository == 'docker.io/foo'
        app.@towerToken == 'xyz'
    }

    def 'should get the correct await duration in minutes'(){
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest", '--await', '10m']

        when:
        def cli = new CommandLine(app)
        cli.registerConverter(Duration.class, new DurationConverter())
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        noExceptionThrown()
        and:
        app.@await == Duration.ofMinutes(10)
    }

    def 'should get the correct await duration in seconds'(){
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest", '--await', '10s']

        when:
        def cli = new CommandLine(app)
        cli.registerConverter(Duration.class, new DurationConverter())
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        noExceptionThrown()
        and:
        app.@await == Duration.ofSeconds(10)
    }

    def 'should get the default await duration'(){
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest", '--await']

        when:
        def cli = new CommandLine(app)
        cli.registerConverter(Duration.class, new DurationConverter())
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        noExceptionThrown()
        and:
        app.@await == Duration.ofMinutes(15)
    }
    
    def 'should generate a container' () {
        given:
        def app = new App()
        String[] args = [ 'Get a docker container']

        when:
        new CommandLine(app).parseArgs(args)
        then:
        app.prompt == ['Get a docker container']
    }

    def 'should get the correct name strategy'(){
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest", "--name-strategy", "tagPrefix"]

        when:
        def cli = new CommandLine(app)
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        noExceptionThrown()
        and:
        app.@nameStrategy == ImageNameStrategy.tagPrefix
    }

    def 'should get the correct name strategy'(){
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest", "--name-strategy", "imageSuffix"]

        when:
        def cli = new CommandLine(app)
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        noExceptionThrown()
        and:
        app.@nameStrategy == ImageNameStrategy.imageSuffix
    }

    def 'should fail when passing incorrect name strategy'(){
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest", "--name-strategy", "wrong"]

        when:
        def cli = new CommandLine(app)
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(CommandLine.ParameterException)
        and:
        e.getMessage() == "Invalid value for option '--name-strategy': expected one of [none, tagPrefix, imageSuffix] (case-sensitive) but was 'wrong'"
    }

    def 'should fail when specifying mirror registry and container file' () {
        given:
        def app = new App()
        String[] args = ["--mirror-registry", "docker.io", "-f", "foo"]

        when:
        def cli = new CommandLine(app)
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        and:
        e.getMessage() == "Argument --mirror-registry and --containerfile conflict each other"
    }

    def 'should fail when specifying mirror registry and conda package' () {
        given:
        def app = new App()
        String[] args = ["--mirror-registry", "docker.io", "--conda-package", "foo"]

        when:
        def cli = new CommandLine(app)
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        and:
        e.getMessage() == "Argument --mirror-registry and --conda-package conflict each other"
    }

    def 'should fail when specifying mirror registry and freeze' () {
        given:
        def app = new App()
        String[] args = ["--mirror-registry", "docker.io", "--image", "foo", "--freeze"]

        when:
        def cli = new CommandLine(app)
        cli.parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        and:
        e.getMessage() == "Argument --mirror-registry and --freeze conflict each other"
    }

    @Unroll
    def 'should check service version'() {
        given:
        def app = new App()
        expect:
        app.serviceVersion0(CURRENT, REQUIRED) == EXPECTED

        where:
        CURRENT     | REQUIRED      | EXPECTED
        '2.0.0'     | '1.1.1'       | '2.0.0'
        '2.0.0'     | '2.0.0'       | '2.0.0'
        '2.0.0'     | '2.1.0'       | '2.0.0 (required: 2.1.0)'
    }

}
