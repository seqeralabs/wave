package io.seqera.wave.core.spec

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ConfigSpecTest extends Specification {

    def 'should create a config spec' () {
        when:
        def config1 = new ConfigSpec()
        then:
        config1.hostName == null
        config1.domainName == null
        config1.workingDir == null
        config1.user == null
        and:
        !config1.attachStdin
        !config1.attachStdout
        !config1.attachStderr
        and:
        config1.env == []
        config1.cmd == []
        config1.entrypoint == []

        when:
        def config2 = new ConfigSpec(
                Hostname: 'foo',
                Domainname: 'bar',
                User: 'Me',
                AttachStdin: true,
                AttachStdout: true,
                AttachStderr: true,
                Cmd: ['this','that'],
                Env: ['Foo=1', 'Bar=2'],
                Entrypoint: ['some','entry']
        )
        then:
        config2.hostName == 'foo'
        config2.domainName == 'bar'
        config2.user == 'Me'
        and:
        config2.attachStdin
        config2.attachStdout
        config2.attachStderr
        and:
        config2.env == ['Foo=1', 'Bar=2']
        config2.cmd == ['this','that']
        config2.entrypoint == ['some','entry']
    }
}
