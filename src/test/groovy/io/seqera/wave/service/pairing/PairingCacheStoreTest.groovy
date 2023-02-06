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
        record.validUntil == now
    }
}
