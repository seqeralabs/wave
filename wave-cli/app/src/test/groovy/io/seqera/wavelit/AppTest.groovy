package io.seqera.wavelit


import java.time.Instant

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wavelit.exception.IllegalCliArgumentException
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
}
