package io.seqera.wavelit

import spock.lang.Specification
import spock.lang.Subject
import picocli.CommandLine

class AppTest extends Specification {

    @Subject
    App app = new App()

    def "test entry point parsing"() {
        given:
        String[] args = ["--config-entrypoint", "entryPoint"]

        when:
        new CommandLine(app).parseArgs(args)

        then:
        app.entryPoint[0] == "entryPoint"
    }
}

