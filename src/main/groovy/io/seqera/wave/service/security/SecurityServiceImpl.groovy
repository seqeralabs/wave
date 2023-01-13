package io.seqera.wave.service.security

import java.security.KeyPair

import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.wave.exchange.PairServiceResponse
import io.seqera.wave.util.DigestFunctions
import jakarta.inject.Inject

/**
 * https://www.baeldung.com/java-rsa
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SecurityServiceImpl implements SecurityService {

    @Inject
    private KeysCacheStore store

    @Override
    PairServiceResponse getPublicKey(String service, String endpoint) {
        final uid =  makeKey(service,endpoint)

        def entry = store.get(uid)
        if (!entry) {
            final keyPair = generate()
            final newEntry = new KeyRecord(service, endpoint, uid, keyPair.getPrivate().getEncoded(),keyPair.getPublic().getEncoded())
            // checking the presence of the entry before the if, only optimizes
            // the hot path, when the key has already been created, but cannot
            // guarantee the correctness in case of multiple concurrent invocations,
            // leading to the unfortunate case where the returned key does not correspond
            // to the stored one. Therefore we need an *atomic/transactional* putIfAbsent here
            entry = store.putIfAbsentAndGetCurrent(uid,newEntry)

        }

        final result = new PairServiceResponse(
                keyId: uid,
                publicKey: entry.publicKey.encodeBase64(),
        )
        return result
    }

    @Override
    KeyRecord getServiceRegistration(String service, String endpoint) {
        final uid = makeKey(service, endpoint)
        return store.get(uid)
    }

    protected static String makeKey(String service, String towerEndpoint) {
        final attrs = [service: service, towerEndpoint: towerEndpoint]
        return DigestFunctions.md5(attrs)
    }

    protected KeyPair generate() {
        final cipher = AsymmetricCipher.getInstance()
        return cipher.generateKeyPair()
    }
}
