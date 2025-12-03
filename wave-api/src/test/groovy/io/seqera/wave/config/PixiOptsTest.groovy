package io.seqera.wave.config

import spock.lang.Specification

class PixiOptsTest extends Specification {

    def "initializes with default values when no options provided"() {
        when:
        def opts = new PixiOpts()

        then:
        opts.pixiImage == PixiOpts.DEFAULT_PIXI_IMAGE
        opts.baseImage == PixiOpts.DEFAULT_BASE_IMAGE
        opts.basePackages == PixiOpts.DEFAULT_PACKAGES
        opts.commands == null
    }

    def "initializes with provided values"() {
        when:
        def opts = new PixiOpts([
                pixiImage: "custom/image:1.0",
                baseImage: "debian:11",
                commands: ["cmd1", "cmd2"],
                basePackages: "custom-package"
        ])

        then:
        opts.pixiImage == "custom/image:1.0"
        opts.baseImage == "debian:11"
        opts.basePackages == "custom-package"
        opts.commands == ["cmd1", "cmd2"]
    }

    def "uses default values for missing options"() {
        when:
        def opts = new PixiOpts([
                pixiImage: "custom/image:1.0"
        ])

        then:
        opts.pixiImage == "custom/image:1.0"
        opts.baseImage == PixiOpts.DEFAULT_BASE_IMAGE
        opts.basePackages == PixiOpts.DEFAULT_PACKAGES
        opts.commands == null
    }

    def "updates pixiImage with withPixiImage"() {
        when:
        def opts = new PixiOpts().withPixiImage("new/image:2.0")

        then:
        opts.pixiImage == "new/image:2.0"
    }

    def "updates commands with withCommands"() {
        when:
        def opts = new PixiOpts().withCommands(["cmd1", "cmd2"])

        then:
        opts.commands == ["cmd1", "cmd2"]
    }

    def "updates basePackages with withBasePackages"() {
        when:
        def opts = new PixiOpts().withBasePackages("new-package")

        then:
        opts.basePackages == "new-package"
    }

    def "toString formats correctly"() {
        when:
        def opts = new PixiOpts([
                pixiImage: "custom/image:1.0",
                baseImage: "debian:11",
                commands: ["cmd1", "cmd2"],
                basePackages: "custom-package"
        ])

        then:
        opts.toString() == "PixiOpts(pixiImage=custom/image:1.0; basePackages=custom-package, commands=cmd1,cmd2, baseImage=debian:11)"
    }

    def "equals and hashCode work for equal objects"() {
        given:
        final opts1 = new PixiOpts([
                pixiImage: "image:1.0",
                baseImage: "ubuntu:20.04",
                commands: ["cmd1"],
                basePackages: "package1"
        ])
        final opts2 = new PixiOpts([
                pixiImage: "image:1.0",
                baseImage: "ubuntu:20.04",
                commands: ["cmd1"],
                basePackages: "package1"
        ])

        expect:
        opts1 == opts2
        opts1.hashCode() == opts2.hashCode()
    }

    def "equals and hashCode work for different objects"() {
        given:
        final opts1 = new PixiOpts([
                pixiImage: "image:1.0",
                baseImage: "ubuntu:20.04",
                commands: ["cmd1"],
                basePackages: "package1"
        ])
        final opts2 = new PixiOpts([
                pixiImage: "image:2.0",
                baseImage: "debian:11",
                commands: ["cmd2"],
                basePackages: "package2"
        ])

        expect:
        opts1 != opts2
        opts1.hashCode() != opts2.hashCode()
    }
}
