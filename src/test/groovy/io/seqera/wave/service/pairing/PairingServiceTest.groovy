package io.seqera.wave.service.pairing

import java.time.Duration

import spock.lang.Specification

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ThreadLocalRandom

import groovy.util.logging.Slf4j
import io.seqera.wave.service.cache.impl.LocalCacheProvider

@Slf4j
class PairingServiceTest extends Specification{

    def 'check security service generates credentials'() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'a security service using it'
        final service = new PairingServiceImpl(store: store, lease: Duration.ofSeconds(100))

        when: 'we get a public key'
        def key = service.getPairingKey("tower","tower.io:9090")

        then: 'we generate a key'
        key.pairingId
        key.publicKey

        and: 'the store contains the key'
        def storedKey = store.get(key.pairingId)

        and: 'the key is associated with the instance the asked for it'
        storedKey.service == 'tower'
        storedKey.endpoint == "tower.io:9090"

        and: 'the key contains the private part of the key pair'
        storedKey.privateKey

        and: 'the keys match'
        keysMatch(key.publicKey, storedKey.privateKey)

    }


    def "skip generate keys if present and not expired"() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'a security service using the cache store'
        final service = new PairingServiceImpl(store: store, lease:  Duration.ofSeconds(1000))

        when: 'we get the key two times for the same service and endpoint'
        def firstKey = service.getPairingKey("tower", "tower.io:9090")
        def secondKey = service.getPairingKey("tower", "tower.io:9090")

        then: 'we generate the key only once'
        firstKey.pairingId == secondKey.pairingId
        firstKey.publicKey == secondKey.publicKey
    }

    def "regenerates keys when they have no validUntil defined"() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'an old non timestamped pairing record coming from previous versions'
        final key = PairingServiceImpl.makeKey('tower','tower.io:9090')
        final pKey = PairingServiceImpl.generate().public.getEncoded()
        store.put(key,new PairingRecord('tower', 'tower.io:9090', key, new byte[0], pKey, null))

        and: 'and a service using the store'
        final service = new PairingServiceImpl(store: store, lease: Duration.ofSeconds(10))

        when: 'we try to generate the same pairing key'
        final pairingKey = service.getPairingKey('tower','tower.io:9090')

        then: 'the record is considered as expired and is generated again'
        pairingKey.pairingId == key
        pairingKey.publicKey.decodeBase64() != pKey
    }

    def "regenerate keys when expired"() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'a security service using the cache store'
        final service = new PairingServiceImpl(store: store, lease: Duration.ofMillis(100))

        when: 'we get the key two times for the same service and endpoint waiting over the ttl interval'
        def firstKey = service.getPairingKey("tower", "tower.io:9090")
        sleep 200
        def secondKey = service.getPairingKey("tower", "tower.io:9090")

        then: 'we regenerate the key'
        firstKey.pairingId == secondKey.pairingId
        firstKey.publicKey != secondKey.publicKey
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
