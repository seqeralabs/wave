/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
