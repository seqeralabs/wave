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

package io.seqera.wavelit

import java.nio.file.Files

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.seqera.wave.api.ContainerConfig
import io.seqera.wavelit.exception.IllegalCliArgumentException
import picocli.CommandLine
import spock.lang.Specification

/**
 * Test App config prefixed options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AppConfigOptsTest extends Specification {

    def CONFIG_JSON = '''\
            {
              "entrypoint": [ "/some", "--entrypoint" ],
              "layers": [
                {
                  "location": "https://location",
                  "gzipDigest": "sha256:gzipDigest",
                  "gzipSize": 100,
                  "tarDigest": "sha256:tarDigest",
                  "skipHashing": true
                }
              ]
            }
            '''

    WireMockServer wireMockServer
    def setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8080))
        wireMockServer.start()

        WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo("/api/data"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(CONFIG_JSON)
                        )
        )
    }

    def cleanup() {
        wireMockServer.stop()
    }


    def "test valid entrypoint"() {
        given:
        def app = new App()
        String[] args = ["--config-entrypoint", "entryPoint"]
        def cli = new CommandLine(app)

        when:
        cli.parseArgs(args)
        then:
        app.@entrypoint == "entryPoint"

        when:
        def config = app.prepareConfig()
        then:
        config == new ContainerConfig(entrypoint: ['entryPoint'])
    }

    def "test invalid entrypoint"() {
        given:
        def app = new App()
        String[] args = ["--config-entrypoint"]

        when:
        new CommandLine(app).parseArgs(args)

        then:
        thrown(CommandLine.MissingParameterException)
    }

    def "test valid command"() {
        given:
        def app = new App()
        String[] args = ["--config-cmd", "/some/command"]

        when:
        new CommandLine(app).parseArgs(args)
        then:
        app.@command == "/some/command"

        when:
        def config = app.prepareConfig()
        then:
        config == new ContainerConfig(cmd: ['/some/command'])
    }

    def "test invalid command"() {
        given:
        def app = new App()
        String[] args = ["--config-cmd", ""]

        when:
        new CommandLine(app).parseArgs(args)
        app.prepareConfig()
        then:
        thrown(IllegalCliArgumentException)

    }

    def "test valid environment"() {
        given:
        def app = new App()
        String[] args = ["--config-env", "var1=value1","--config-env", "var2=value2"]

        when:
        new CommandLine(app).parseArgs(args)
        then:
        app.@environment[0] == "var1=value1"
        app.@environment[1] == "var2=value2"

        when:
        def config = app.prepareConfig()
        then:
        config == new ContainerConfig(env: ['var1=value1', 'var2=value2'])
    }

    def "test invalid environment"() {
        given:
        def app = new App()
        String[] args = ["--config-env", "VAR"]

        when:
        new CommandLine(app).parseArgs(args)
        app.prepareConfig()
        then:
        def e = thrown(IllegalCliArgumentException)
        e.message == 'Invalid environment variable syntax - offending value: VAR'

    }

    def "test valid working directory"() {
        given:
        def app = new App()
        String[] args = ["--config-working-dir", "/work/dir"]

        when:
        new CommandLine(app).parseArgs(args)
        then:
        app.@workingDir == "/work/dir"

        when:
        def config = app.prepareConfig()
        then:
        config == new ContainerConfig(workingDir: '/work/dir')
    }

    def "test invalid working directory"() {
        given:
        def app = new App()
        String[] args = ["--config-working-dir", "  "]

        when:
        new CommandLine(app).parseArgs(args)
        app.prepareConfig()
        then:
        thrown(IllegalCliArgumentException)

    }

    def "test valid config file from a path"() {
        given:
        def folder = Files.createTempDirectory('test')
        def configFile = folder.resolve('config.json')
        configFile.text = CONFIG_JSON
        and:
        def app = new App()
        String[] args = ["--config-file", configFile.toString()]

        when:
        new CommandLine(app).parseArgs(args)
        then:
        app.@configFile == configFile.toString()

        when:
        def config = app.prepareConfig()
        then:
        config.entrypoint == [ "/some", "--entrypoint" ]
        def layer = config.layers[0]
        layer.location == "https://location"
        layer.gzipDigest == "sha256:gzipDigest"
        layer.tarDigest == "sha256:tarDigest"
        layer.gzipSize == 100

        cleanup:
        folder?.deleteDir()
    }

    def "test valid config file from a URL"() {
        given:
        def app = new App()
        String[] args = ["--config-file", "http://localhost:8080/api/data"]

        when:
        new CommandLine(app).parseArgs(args)
        then:
        app.@configFile == "http://localhost:8080/api/data"

        when:
        def config = app.prepareConfig()
        then:
        config.entrypoint == [ "/some", "--entrypoint" ]
        def layer = config.layers[0]
        layer.location == "https://location"
        layer.gzipDigest == "sha256:gzipDigest"
        layer.tarDigest == "sha256:tarDigest"
        layer.gzipSize == 100
    }
}
