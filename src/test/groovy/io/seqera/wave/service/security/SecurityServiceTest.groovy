package io.seqera.wave.service.security

import spock.lang.Specification

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ThreadLocalRandom

import groovy.util.logging.Slf4j
import io.seqera.wave.service.cache.impl.LocalCacheProvider

@Slf4j
class SecurityServiceTest extends Specification{

    def 'check security service generates credentials'() {
        given: 'a cache store'
        final store = new KeysCacheStore(new LocalCacheProvider())

        and: 'a security service using it'
        final service = new SecurityServiceImpl(store: store)

        when: 'we get a public key'
        def key = service.getPublicKey("tower","instance-id","tower.io:9090")

        then: 'we generate a key'
        key.keyId
        key.publicKey

        and: 'the store contains the key'
        def storedKey = store.get(key.keyId)

        and: 'the key is associated with the instance the asked for it'
        storedKey.service == 'tower'
        storedKey.hostname == "tower.io:9090"
        storedKey.instanceId == 'instance-id'

        and: 'the key contains the private part of the key pair'
        storedKey.privateKey

        and: 'the keys match'
        keysMatch(key.publicKey, storedKey.privateKey)


    }


    private static boolean keysMatch(String encodedPublicKey, byte[] encodedPrivateKey) {
        // we decode the keys
        def kf = KeyFactory.getInstance('RSA')
        def priv = kf.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey))
        def pub = kf.generatePublic(new X509EncodedKeySpec(Base64.decoder.decode(encodedPublicKey)))

        // we create a challenge
        byte[] challenge = new byte[1024];
        ThreadLocalRandom.current().nextBytes(challenge)

        // sign it with private key
        def sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(priv)
        sig.update(challenge)
        def signature = sig.sign();

        // and verify it with the public key
        sig.initVerify(pub)
        sig.update(challenge)

        // they match
        return sig.verify(signature)
    }
}
