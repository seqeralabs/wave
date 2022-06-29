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
class AccessTokenDaoTest extends Specification {

    @Inject @Shared UserDao userDao
    @Inject @Shared AccessTokenDao accessTokenDao

    def 'should save and get access token' () {
        given:
        def usr1 = new User(id: 1, userName: 'me', email: 'me@google.com', deleted: false )
        userDao.save(usr1)
        and:
        def usr2 = new User(id: 2, userName: 'you', email: 'you@google.com', deleted: false )
        userDao.save(usr2)

        and:
        accessTokenDao.save(
                new AccessToken(id: 100, user:usr1, name: 'foo', secret: 'secret1'.bytes) )
        accessTokenDao.save(
                new AccessToken(id: 200, user:usr2, name: 'bar', secret: 'secret2'.bytes) )
        accessTokenDao.save(
                new AccessToken(id: 300, user:usr1, name: 'baz', secret: 'secret4'.bytes) )

        when:
        def tokens = accessTokenDao.findAll()
        then:
        tokens.size() == 3

        when:
        def t1 = accessTokenDao.findById(100).orElse(null)
        then:
        t1.name == 'foo'
        t1.user.id == 1

        when:
        def t2 = accessTokenDao.findById(200).orElse(null)
        then:
        t2.name == 'bar'
        t2.user.id == 2

        when:
        def t3 = accessTokenDao.findById(300).orElse(null)
        then:
        t3.name == 'baz'
        t3.user.id == 1

    }

}
