/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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
        final endpoint0 = patchPlatformEndpoint(endpoint)
        final key = makeKey(service, endpoint0)

        def entry = store.get(key)
        if (!entry || entry.isExpired()) {
            final pairingId = LongRndKey.rndLong().toString()
            log.debug "Pairing with service '${service}' at address $endpoint0 - pairing id: $pairingId (key: $key)"
            final keyPair = generate()
            final expiration = Instant.now() + lease
            final newEntry = new PairingRecord(service, endpoint0, pairingId, keyPair.getPrivate().getEncoded(), keyPair.getPublic().getEncoded(), expiration)
            store.put(key,newEntry)
            entry = newEntry
        } else {
            log.trace "Paired already with service '${service}' at address $endpoint0 - pairing id: $entry.pairingId (key: $key)"
        }

        return new PairingResponse( pairingId: entry.pairingId, publicKey: entry.publicKey.encodeBase64() )
    }

    @Override
    PairingRecord getPairingRecord(String service, String endpoint) {
        final uid = makeKey(service, patchPlatformEndpoint(endpoint))
        return store.get(uid)
    }

    static String patchPlatformEndpoint(String endpoint) {
        // api.stage-tower.net --> api.cloud.stage-seqera.io
        // api.tower.nf --> api.cloud.seqera.io
        final result = endpoint
                .replace('/api.stage-tower.net','/api.cloud.stage-seqera.io')
                .replace('/api.tower.nf','/api.cloud.seqera.io')
        if( result != endpoint )
            log.debug "Patched Platform endpoint: '$endpoint' with '$result'"
        return result
    }

    protected static String makeKey(String service, String endpoint) {
        final attrs = [service: service, towerEndpoint: endpoint] as Map<String,Object>
        return DigestFunctions.md5(attrs)
    }

    protected static KeyPair generate() {
        final cipher = AsymmetricCipher.getInstance()
        return cipher.generateKeyPair()
    }
}
