package io.seqera.wave.service.security

import java.security.KeyPair
import java.security.KeyPairGenerator

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
    RegisterInstanceResponse getPublicKey(String service, String instanceId, String hostName) {
        final attrs = [service:service, instanceId:instanceId, hostName: hostName]
        final uid =  DigestFunctions.md5(attrs)
        // NOTE: we may want change this. ideally a new key-pair should be created only
        // if does not exist yet
        final keyPair = generate()
        final entry = new KeyRecord(service, instanceId, hostName, keyPair.getPrivate().getEncoded())
        store.put(uid, entry)
        final result = new RegisterInstanceResponse(
                keyId: uid,
                publicKey: keyPair.getPublic().getEncoded().encodeBase64(),
        )
        return result
    }


    protected KeyPair generate() {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair()
    }
}
