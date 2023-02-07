package io.seqera.wave.service.pairing

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.ToString

/**
 * Model a security key record associated with a registered service endpoint
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(excludes = 'privateKey')
class PairingRecord {
    String service
    String endpoint
    String pairingId
    byte[] privateKey
    byte[] publicKey
    Instant expiration

    boolean isExpiredAt(Instant time) {
        return expiration == null || expiration.isBefore(time)
    }

    boolean isExpired() {
        return isExpiredAt(Instant.now())
    }
}
