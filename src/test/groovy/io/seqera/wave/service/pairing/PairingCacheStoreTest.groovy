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

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.service.cache.impl.LocalCacheProvider

class PairingCacheStoreTest extends Specification{

    def 'pairing cache store properly serializes/deserializes pairing record'() {
        given: 'a store'
        final store = new PairingCacheStore(new LocalCacheProvider())
        when: 'we put and get back a record'
        def now = Instant.now()
        store.put('key', new PairingRecord('tower','endpoint','pairingId', new byte[0], new byte[0],now))
        final record = store.get('key')

        then:
        record.service == 'tower'
        record.endpoint == 'endpoint'
        record.pairingId == 'pairingId'
        record.publicKey.length == 0
        record.privateKey.length == 0
        record.expiration == now
    }
}
