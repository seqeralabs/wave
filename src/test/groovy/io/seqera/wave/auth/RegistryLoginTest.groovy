package io.seqera.wave.auth

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class RegistryLoginTest extends Specification{
    @Inject
    RegistryAuthServiceImpl impl

    void 'test login with registry'() {
        when:
        def login = impl.login("docker.io","wavetest","dckr_pat_sShAQOWshE-y3SeE8wll774CWzM")

        then:
        login
    }
    void 'test valid login with repository'() {
        when:
        def login = impl.login("docker.io/hrma017/dev","hrma017","dckr_pat_NtfDznNlQjarjit3df4L713undw")
        then:
        login
    }
    void 'test invalid login with repository'() {
        when:
        def login = impl.login("docker.io/hrma017/dev","wavetest","dckr_pat_sShAQOWshE-y3SeE8wll774CWzM")
        then:
        !login
    }
}
