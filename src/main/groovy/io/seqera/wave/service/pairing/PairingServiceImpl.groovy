/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements the pairing service for Tower and Wave credentials federation
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingServiceImpl implements PairingService {

    @Inject
    private PairingCacheStore store

    /**
     * The period of time after which the token should be renewed
     */
    @Value('${wave.pairing-key.lease:`1d`}')
    private Duration lease


    @Override
    PairingResponse acquirePairingKey(String service, String endpoint) {
        final key = makeKey(service,endpoint)

        def entry = store.get(key)
        if (!entry || entry.isExpired()) {
            final pairingId = LongRndKey.rndLong().toString()
            log.debug "Pairing with service '${service}' at address $endpoint - pairing id: $pairingId (key: $key)"
            final keyPair = generate()
            final expiration = Instant.now() + lease
            final newEntry = new PairingRecord(service, endpoint, pairingId, keyPair.getPrivate().getEncoded(), keyPair.getPublic().getEncoded(), expiration)
            store.put(key,newEntry)
            entry = newEntry
        } else {
            log.trace "Paired already with service '${service}' at address $endpoint - pairing id: $entry.pairingId (key: $key)"
        }

        return new PairingResponse( pairingId: entry.pairingId, publicKey: entry.publicKey.encodeBase64() )
    }

    @Override
    PairingRecord getPairingRecord(String service, String endpoint) {
        final uid = makeKey(service, endpoint)
        return store.get(uid)
    }

    protected static String makeKey(String service, String towerEndpoint) {
        final attrs = [service: service, towerEndpoint: towerEndpoint] as Map<String,Object>
        return DigestFunctions.md5(attrs)
    }

    protected static KeyPair generate() {
        final cipher = AsymmetricCipher.getInstance()
        return cipher.generateKeyPair()
    }
}
