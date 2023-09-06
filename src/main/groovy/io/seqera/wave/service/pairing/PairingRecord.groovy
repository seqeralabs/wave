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
