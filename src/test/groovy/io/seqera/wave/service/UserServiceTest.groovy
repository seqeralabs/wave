package io.seqera.wave.service

import spock.lang.Ignore
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.UnauthorizedException

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Ignore
@MicronautTest
class UserServiceTest extends Specification {

    def 'should auth user' () {
        given:
        def ctx = ApplicationContext.run('tower')
        and:
        def service = ctx.getBean(UserService)

        when:
        def user = service.getUserByAccessToken("eyJ0aWQiOiA1Mjc5fS5jYmIzMzc3YzcxZGY1N2ZkMTAzNTEzYWVjMWVhYjBkMDUxODMwMzA5")
        then:
        user.id == 1

        when:
        service.getUserByAccessToken("foo")
        then:
        thrown(UnauthorizedException)
    }

}
