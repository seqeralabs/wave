package io.seqera.wave.tower.mysql

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.tower.crypto.CryptoHelper
import io.seqera.wave.tower.Credentials
import io.seqera.wave.tower.CredentialsDao
import io.seqera.wave.tower.SecretDao
import io.seqera.wave.tower.User
import io.seqera.wave.tower.UserDao
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'mysql')
class MySqlCredentialsDaoTest extends Specification {

    @Inject UserDao userDao
    @Inject CredentialsDao credentialsDao
    @Inject SecretDao secretDao

    def 'should find creds by user' () {
        given:
        def USER_ID = 10
        def SALT = CryptoHelper.rndSalt()
        and:
        // create a user
        def user = userDao.save(
                new User(id: USER_ID, userName: 'foo', email: 'foo@gmail.com'))

        // create the credentials
        def creds1 = credentialsDao.save(
                new Credentials( id: 'abc', name: 'foo', provider: 'container-reg', salt: SALT, keys: '{foo}', user: user ))
        and:
        def creds2 = credentialsDao.save(
                new Credentials( id: 'cba', name: 'bar', provider: 'container-reg', salt: SALT, keys: '{bar}', user: user ))

        and:
        def creds3 = credentialsDao.save(
                new Credentials( id: 'cba', name: 'bar', provider: 'container-reg', salt: SALT, keys: '{bar}', user: user, workspaceId: 100 ))


        when:
        def result = credentialsDao.findRegistryCredentialsByUser(USER_ID)
        then:
        result.size() == 2
    }

    def 'should find creds by workspace' () {
        given:
        def USER_ID = 10
        def WORKSPACE_1 = 1
        def WORKSPACE_2 = 2

        def SALT = CryptoHelper.rndSalt()
        and:
        // create a user
        def user = userDao.save(
                new User(id: USER_ID, userName: 'foo', email: 'foo@gmail.com'))

        // create the credentials
        def creds1 = credentialsDao.save(
                new Credentials( id: 'abc', name: 'foo', provider: 'container-reg', salt: SALT, keys: '{foo}', user: user ))
        and:
        def creds2 = credentialsDao.save(
                new Credentials( id: 'cba', name: 'bar', provider: 'container-reg', salt: SALT, keys: '{bar}', user: user, workspaceId: WORKSPACE_1 ))
        and:
        def creds3 = credentialsDao.save(
                new Credentials( id: 'cba', name: 'bar', provider: 'container-reg', salt: SALT, keys: '{bar}', user: user, workspaceId: WORKSPACE_2 ))

        when:
        def result = credentialsDao.findRegistryCredentialsByWorkspaceId(WORKSPACE_1)
        then:
        result.size() == 1
        and:
        result.get(0).id == creds2.id

        when:
        result = credentialsDao.findRegistryCredentialsByWorkspaceId(WORKSPACE_2)
        then:
        result.size() == 1
        and:
        result.get(0).id == creds3.id
    }

}
