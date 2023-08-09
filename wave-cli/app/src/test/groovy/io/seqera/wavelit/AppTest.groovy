package io.seqera.wavelit

import spock.lang.Specification
import spock.lang.Subject
import picocli.CommandLine

class AppTest extends Specification {

    @Subject
    App app = new App()

    def "test valid command"() {
        given:
        String[] args = ["--config-command", "command"]

        when:
        new CommandLine(app).parseArgs(args)

        then:
        app.command == "command"
    }

    def "test invalid command"() {
        given:
        String[] args = ["--config-command"]

        when:
        new CommandLine(app).parseArgs(args)

        then:
        thrown(CommandLine.MissingParameterException)
    }
}
