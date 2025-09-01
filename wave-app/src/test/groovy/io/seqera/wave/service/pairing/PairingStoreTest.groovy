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

import spock.lang.Specification

import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.store.state.impl.LocalStateProvider
import jakarta.inject.Inject

@MicronautTest
class PairingStoreTest extends Specification{

    @Inject
    PairingStore store

    def 'should return entry key' () {
        expect:
        store.key0('foo') == 'pairing-keys/v1:foo'
    }

    def 'pairing cache store properly serializes/deserializes pairing record'() {
        given: 'a store'
        final store = new PairingStore(new LocalStateProvider())
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
