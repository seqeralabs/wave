/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import io.seqera.wave.util.LongRndKey

@Slf4j
class PairingServiceTest extends Specification{

    def 'check security service generates credentials'() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'a security service using it'
        final service = new PairingServiceImpl(store: store, lease: Duration.ofSeconds(100))

        when: 'we get a public key'
        def key = PairingServiceImpl.makeKey("tower","tower.io:9090")
        def response = service.acquirePairingKey("tower","tower.io:9090")

        then: 'we generate a key'
        response.pairingId
        response.publicKey

        and: 'the store contains the key'
        def storedKey = store.get(key)

        and: 'the key is associated with the instance the asked for it'
        storedKey.service == 'tower'
        storedKey.endpoint == "tower.io:9090"
        storedKey.pairingId == response.pairingId
        
        and: 'the key contains the private part of the key pair'
        storedKey.privateKey

        and: 'the keys match'
        keysMatch(response.publicKey, storedKey.privateKey)

    }


    def "skip generate keys if present and not expired"() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'a security service using the cache store'
        final service = new PairingServiceImpl(store: store, lease:  Duration.ofSeconds(1000))

        when: 'we get the key two times for the same service and endpoint'
        def firstKey = service.acquirePairingKey("tower", "tower.io:9090")
        def secondKey = service.acquirePairingKey("tower", "tower.io:9090")

        then: 'we generate the key only once'
        firstKey.pairingId == secondKey.pairingId
        firstKey.publicKey == secondKey.publicKey
    }

    def "regenerates keys when they have no validUntil defined"() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'an old non timestamped pairing record coming from previous versions'
        final key = PairingServiceImpl.makeKey('tower','tower.io:9090')
        final pairingId = LongRndKey.rndLong().toString()
        final pKey = PairingServiceImpl.generate().public.getEncoded()
        store.put(key,new PairingRecord('tower', 'tower.io:9090', pairingId, new byte[0], pKey, null))

        and: 'and a service using the store'
        final service = new PairingServiceImpl(store: store, lease: Duration.ofSeconds(10))

        when: 'we try to generate the same pairing key'
        final pairingKey = service.acquirePairingKey('tower','tower.io:9090')

        then: 'the record is considered as expired and is generated again'
        pairingKey.pairingId != pairingId
        pairingKey.publicKey.decodeBase64() != pKey
    }

    def "regenerate keys when expired"() {
        given: 'a cache store'
        final store = new PairingCacheStore(new LocalCacheProvider())

        and: 'a security service using the cache store'
        final service = new PairingServiceImpl(store: store, lease: Duration.ofMillis(100))

        when: 'we get the key two times for the same service and endpoint waiting over the ttl interval'
        def firstKey = service.acquirePairingKey("tower", "tower.io:9090")
        sleep 200
        def secondKey = service.acquirePairingKey("tower", "tower.io:9090")

        then: 'we regenerate the key'
        firstKey.pairingId != secondKey.pairingId
        firstKey.publicKey != secondKey.publicKey
    }

    def 'should generate the same hash key' () {

        expect:
        100.times {
        assert PairingServiceImpl.makeKey('tower', 'https://api.tower.nf') == '7a0ebf8c7ef4b89227a0f6700d4322cb'
        }

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
