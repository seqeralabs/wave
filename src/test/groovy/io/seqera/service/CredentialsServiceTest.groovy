package io.seqera.service

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class CredentialsServiceTest extends Specification {

    @Inject CredentialsService credentialsService

    def 'should registry creds' () {
        when:
        def result = credentialsService.findRegistryCreds('quay.io', 1, 123230369066943)
        println result.password
        then:
        result.password
    }
}
