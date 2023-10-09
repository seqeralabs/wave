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

import io.seqera.wave.cli.exception.IllegalCliArgumentException
import picocli.CommandLine
import spock.lang.Specification
/**
 * Test App Spack prefixed options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AppSpackOptsTest extends Specification {

    def 'should fail when passing both spack file and packages' () {
        given:
        def app = new App()
        String[] args = ["--spack-file", "foo", "--spack-package", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both spack file and image' () {
        given:
        def app = new App()
        String[] args = ["--spack-file", "foo", "--image", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both spack file and conmtainer file' () {
        given:
        def app = new App()
        String[] args = ["--spack-file", "foo", "--containerfile", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both spack package and image' () {
        given:
        def app = new App()
        String[] args = ["--spack-package", "foo", "--image", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should fail when passing both spack package and conmtainer file' () {
        given:
        def app = new App()
        String[] args = ["--spack-package", "foo", "--containerfile", "bar"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        app.validateArgs()
        then:
        thrown(IllegalCliArgumentException)
    }

    def 'should create container file from spack file' () {
        given:
        def SPACK_FILE = '''\
                spack:
                  specs: [foo, bar]
                  concretizer: {unify: true, reuse: false}
            '''.stripIndent(true)
        and:
        def folder = Files.createTempDirectory('test')
        def spackFile = folder.resolve('spack.yml');
        spackFile.text = SPACK_FILE
        and:
        def app = new App()
        String[] args = ["--spack-file", spackFile.toString()]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()).startsWith("# Runner image")

        and:
        new String(req.spackFile.decodeBase64()) == SPACK_FILE

        cleanup:
        folder?.deleteDir()
    }


    def 'should create container file from spack package' () {
        given:
        def app = new App()
        String[] args = ["--spack-package", "foo"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        def spec = new String(req.containerFile.decodeBase64()).tokenize('\n')
        spec[0] == '# Runner image'
        spec[1] == 'FROM {{spack_runner_image}}'
        spec[2] == 'COPY --from=builder /opt/spack-env /opt/spack-env'

        and:
        new String(req.spackFile.decodeBase64()) == '''\
                spack:
                  specs: [foo]
                  concretizer: {unify: true, reuse: false}
                '''.stripIndent(true)
    }

    def 'should create container file from spack package and custom options' () {
        given:
        def app = new App()
        String[] args = [
                "--spack-package", "foo",
                "--spack-package", "bar",
                "--spack-run-command", "RUN one",
                "--spack-run-command", "RUN two",
        ]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        new String(req.containerFile.decodeBase64()).startsWith("# Runner image")
        new String(req.containerFile.decodeBase64()).contains("RUN one\n")
        new String(req.containerFile.decodeBase64()).contains("RUN two\n")

        and:
        new String(req.spackFile.decodeBase64()) == '''\
                spack:
                  specs: [foo, bar]
                  concretizer: {unify: true, reuse: false}
                '''.stripIndent(true)
    }

    def 'should create container file from spack package with singularity' () {
        given:
        def app = new App()
        String[] args = ["--spack-package", "foo", "--singularity"]

        when:
        new CommandLine(app).parseArgs(args)
        and:
        def req = app.createRequest()
        then:
        def spec = new String(req.containerFile.decodeBase64()).tokenize('\n')
        spec[0] == 'Bootstrap: docker'
        spec[1] == 'From: {{spack_runner_image}}'
        spec[2] == 'stage: final'

        and:
        new String(req.spackFile.decodeBase64()) == '''\
                spack:
                  specs: [foo]
                  concretizer: {unify: true, reuse: false}
                '''.stripIndent(true)
    }

}
