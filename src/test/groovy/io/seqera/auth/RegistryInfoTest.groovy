package io.seqera.auth

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegistryInfoTest extends Specification {

    def 'should parse registry info'() {
        expect:
        RegistryAuth.parse('Bearer realm="https://quay.io/v2/auth",service="quay.io"') == new RegistryAuth(new URI('https://quay.io/v2/auth'), 'quay.io', RegistryAuth.Type.Bearer)
        RegistryAuth.parse('Bearer realm="https://auth.docker.io/token",service="registry.docker.io"') == new RegistryAuth(new URI('https://auth.docker.io/token'), 'registry.docker.io', RegistryAuth.Type.Bearer)
        and:
        RegistryAuth.parse('Basic realm="http://foo",service="bar"') == new RegistryAuth(new URI('http://foo'), 'bar', RegistryAuth.Type.Basic)
        and:
        RegistryAuth.parse('foo') == null
        RegistryAuth.parse(null) == null
    }
}
