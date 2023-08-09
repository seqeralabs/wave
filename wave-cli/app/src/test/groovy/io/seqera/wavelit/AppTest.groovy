package io.seqera.wavelit

import spock.lang.Specification
import spock.lang.Subject
import picocli.CommandLine

class AppTest extends Specification {

    @Subject
    App app = new App()

    def "test valid entrypoint"() {
        given:
        String[] args = ["--config-entrypoint", "entryPoint"]

        when:
        new CommandLine(app).parseArgs(args)

        then:
        app.entrypoint == "entryPoint"
    }

    def "test invalid entrypoint"() {
        given:
        String[] args = ["--config-entrypoint"]

        when:
        new CommandLine(app).parseArgs(args)

        then:
        thrown(CommandLine.MissingParameterException)
    }
}

