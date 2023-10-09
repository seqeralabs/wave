/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.cli

import java.nio.file.Files
import java.time.Instant

import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.util.TarUtils
import io.seqera.wave.cli.exception.IllegalCliArgumentException
import picocli.CommandLine
import spock.lang.Specification

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
                buildId: '98765'
        )

        when:
        new CommandLine(app).parseArgs(args)
        def result = app.dumpOutput(resp)
        then:
        result == '''\
            buildId: '98765'
            containerImage: docker.io/some/container
            containerToken: '12345'
            expiration: '1970-01-20T13:57:19.913Z'
            targetImage: docker.io/some/repo
            '''.stripIndent(true)
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

    def 'should not allow dry-run and await' () {
        given:
        def app = new App()
        String[] args = ["-i", "ubuntu:latest","--dry-run", '--await']

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message == 'Options --dry-run and --await conflicts each other'
    }
}
