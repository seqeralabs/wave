package io.seqera.wave.service

import spock.lang.Specification

import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.tower.crypto.CryptoHelper
import io.seqera.wave.service.ContainerRegistryKeys
import io.seqera.wave.service.CredentialServiceImpl
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.tower.Credentials
import io.seqera.wave.tower.CredentialsDao
import io.seqera.wave.tower.Secret
import io.seqera.wave.tower.SecretDao
import io.seqera.wave.tower.User
import io.seqera.wave.tower.UserDao
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['tower','test', 'mysql'])
class CredentialsServiceTest extends Specification {

    @Inject CredentialsService credentialsService

    @Inject UserDao userDao
    @Inject CredentialsDao credentialsDao
    @Inject SecretDao secretDao
    @Value('${tower.crypto.secretKey}')
    private String secretKey


    def 'should registry creds' () {
        given:
        def crypto = new CryptoHelper(secretKey)
        def USER_ID = 10
        def CREDS_ID = LongRndKey.rndHex()
        def SALT = CryptoHelper.rndSalt()
        def DATA = '{"userName":"me", "password":"you", "registry":"quay.io"}'
        def KEYS = '{"userName":"me", "registry":"quay.io"}'
        and:
        // create a user
        def user = userDao.save(
                new User(id: USER_ID, userName: 'foo', email: 'foo@gmail.com'))
        // create the secret
        def secretKey = "/user/${USER_ID}/creds/${CREDS_ID}"
        def hashedKey = CryptoHelper.encodeSecret(secretKey, SALT).getDataString()
        def secure = crypto.encrypt(DATA, SALT)
        def secret = secretDao.save(
                new Secret(id:hashedKey, secure: secure.serialize()))
        // create the credentials
        def creds = credentialsDao.save(
                new Credentials( id: CREDS_ID, name: 'foo', provider: 'container-reg', salt: SALT, keys: KEYS, user: user ))

        when:
        def result = credentialsService.findRegistryCreds('quay.io', USER_ID, null)
        then:
        result instanceof ContainerRegistryKeys
        result.userName == 'me'
        result.password == 'you'
        result.registry == 'quay.io'
    }

    def 'should parse credentials payload' () {
        given:
        def svc = new CredentialServiceImpl()

        when:
        def keys = svc.parsePayload('{"registry":"foo.io", "userName":"me", "password": "you"}')
        then:
        keys.registry == 'foo.io'
        keys.userName == 'me'
        keys.password == 'you'
    }
}
