/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing

import java.security.KeyPair
import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.service.pairing.exchange.PairingResponse
import io.seqera.wave.util.DigestFunctions
import io.seqera.random.LongRndKey
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
    private PairingStore store

    @Inject
    private PairingConfig config


    @Override
    PairingResponse acquirePairingKey(String service, String endpoint) {
        final key = makeKey(service,endpoint)

        def entry = store.get(key)
        if (!entry || entry.isExpired()) {
            final pairingId = LongRndKey.rndLong().toString()
            log.debug "Pairing with service '${service}' at address $endpoint - pairing id: $pairingId (key: $key)"
            final keyPair = generate()
            final expiration = Instant.now() + config.keyLease
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
