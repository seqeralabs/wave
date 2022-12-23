package io.seqera.wave.service.security

import java.security.KeyPair

import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.wave.exchange.RegisterInstanceResponse
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
    RegisterInstanceResponse getPublicKey(String service, String hostName) {
        final uid =  makeKey(service,hostName)
        // NOTE: we may want change this. ideally a new key-pair should be created only
        // if does not exist yet
        final keyPair = generate()
        final entry = new KeyRecord(service, hostName,uid, keyPair.getPrivate().getEncoded())
        store.put(uid, entry)
        final result = new RegisterInstanceResponse(
                keyId: uid,
                publicKey: keyPair.getPublic().getEncoded().encodeBase64(),
        )
        return result
    }

    @Override
    KeyRecord getServiceRegistration(String service, String towerEndpoint) {
        final uid = makeKey(service, towerEndpoint)
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
