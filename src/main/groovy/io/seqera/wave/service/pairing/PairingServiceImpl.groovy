package io.seqera.wave.service.pairing

import java.security.KeyPair
import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.wave.exchange.PairingResponse
import io.seqera.wave.util.DigestFunctions
import jakarta.inject.Inject
/**
 * Implements the pairing service for Tower and Wave credentials federation
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class PairingServiceImpl implements PairingService {

    @Inject
    private PairingCacheStore store

    @Value('${wave.pairing-key.ttl:`1d`}')
    private Duration pairingKeyTtl


    @Override
    PairingResponse getPairingKey(String service, String endpoint) {
        final uid =  makeKey(service,endpoint)

        def entry = store.get(uid)
        if (!entry || entry.isExpired()) {
            log.debug "Pairing with service '${service}' at address $endpoint - pairing id: $uid"
            final keyPair = generate()
            final issuedAt = Instant.now() + pairingKeyTtl
            final newEntry = new PairingRecord(service, endpoint, uid, keyPair.getPrivate().getEncoded(), keyPair.getPublic().getEncoded(),issuedAt.toEpochMilli())
            store.put(uid,newEntry)
            entry = newEntry
        } else {
            log.trace "Paired already with service '${service}' at address $endpoint - pairing id: $uid"
        }

        return new PairingResponse( pairingId: uid, publicKey: entry.publicKey.encodeBase64() )
    }

    @Override
    PairingRecord getPairingRecord(String service, String endpoint) {
        final uid = makeKey(service, endpoint)
        return store.get(uid)
    }

    protected static String makeKey(String service, String towerEndpoint) {
        final attrs = Map.<String,Object>of('service', service, 'towerEndpoint', towerEndpoint)
        return DigestFunctions.md5(attrs)
    }

    protected KeyPair generate() {
        final cipher = AsymmetricCipher.getInstance()
        return cipher.generateKeyPair()
    }
}
