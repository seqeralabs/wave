package io.seqera.wave.config

import spock.lang.Specification

class CranOptsTest extends Specification {

    def "initializes with default values when no options provided"() {
        when:
        def opts = new CranOpts()

        then:
        opts.rImage == CranOpts.DEFAULT_R_IMAGE
        opts.basePackages == CranOpts.DEFAULT_PACKAGES
        opts.commands == null
    }

    def "initializes with provided values"() {
        when:
        def opts = new CranOpts([
                rImage: "custom/r-image:1.0",
                commands: ["cmd1", "cmd2"],
                basePackages: "custom-package"
        ])

        then:
        opts.rImage == "custom/r-image:1.0"
        opts.basePackages == "custom-package"
        opts.commands == ["cmd1", "cmd2"]
    }

    def "uses default values for missing options"() {
        when:
        def opts = new CranOpts([
                rImage: "custom/r-image:1.0"
        ])

        then:
        opts.rImage == "custom/r-image:1.0"
        opts.basePackages == CranOpts.DEFAULT_PACKAGES
        opts.commands == null
    }

    def "updates rImage with withRImage"() {
        when:
        def opts = new CranOpts().withRImage("new/r-image:2.0")

        then:
        opts.rImage == "new/r-image:2.0"
    }

    def "updates commands with withCommands"() {
        when:
        def opts = new CranOpts().withCommands(["cmd1", "cmd2"])

        then:
        opts.commands == ["cmd1", "cmd2"]
    }

    def "updates basePackages with withBasePackages"() {
        when:
        def opts = new CranOpts().withBasePackages("new-package")

        then:
        opts.basePackages == "new-package"
    }

    def "toString formats correctly"() {
        when:
        def opts = new CranOpts([
                rImage: "custom/r-image:1.0",
                commands: ["cmd1", "cmd2"],
                basePackages: "custom-package"
        ])

        then:
        opts.toString() == "CranOpts(rImage=custom/r-image:1.0; basePackages=custom-package, commands=cmd1,cmd2)"
    }

    def "equals and hashCode work for equal objects"() {
        given:
        def opts1 = new CranOpts([
                rImage: "image:1.0",
                commands: ["cmd1"],
                basePackages: "package1"
        ])
        def opts2 = new CranOpts([
                rImage: "image:1.0",
                commands: ["cmd1"],
                basePackages: "package1"
        ])

        expect:
        opts1 == opts2
        opts1.hashCode() == opts2.hashCode()
    }

    def "equals and hashCode work for different objects"() {
        given:
        def opts1 = new CranOpts([
                rImage: "image:1.0",
                commands: ["cmd1"],
                basePackages: "package1"
        ])
        def opts2 = new CranOpts([
                rImage: "image:2.0",
                commands: ["cmd2"],
                basePackages: "package2"
        ])

        expect:
        opts1 != opts2
        opts1.hashCode() != opts2.hashCode()
    }
}
