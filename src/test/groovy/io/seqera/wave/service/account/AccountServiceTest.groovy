package io.seqera.wave.service.account

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class AccountServiceTest extends Specification {

    @Inject
    AccountService accountService


    def 'should validate auth service' () {
        expect:
        !accountService.isAuthorised(null,null)
        !accountService.isAuthorised('foo','foo')
        and:
        // check the config "application-test.yml" for these accounts
        accountService.isAuthorised('foo','hello')
        accountService.isAuthorised('bar','world')
    }

    def 'should digest string' () {
        given:
        def service = new AccountServiceImpl()
        expect:
        service.digest('hello') == '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'
        service.digest('world') == '486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7'
    }

}
