package io.seqera.tower

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class UserDaoTest extends Specification{

    @Inject @Shared UserDao userDao

    def 'should access users' () {
        when:
        def users = userDao.findAll()
        and:
        println "Users: ${users.toList().id}"
        println "Tokens: ${users.toList().get(0).accessTokens}"
        then:
        users.size()>0

    }

}
