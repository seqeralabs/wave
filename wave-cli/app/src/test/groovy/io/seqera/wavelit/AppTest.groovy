package io.seqera.wavelit


import java.time.Instant

import io.seqera.wave.api.SubmitContainerTokenResponse
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

}
