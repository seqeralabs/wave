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

package io.seqera.service.pairing.exchange

import spock.lang.Specification
import spock.lang.Unroll

import io.seqera.serde.moshi.MoshiEncodeStrategy

/**
 * Tests for exchange model serialization
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PairingExchangeSerializationTest extends Specification {

    def 'should serialize and deserialize PairingRequest'() {
        given:
        def encoder = new MoshiEncodeStrategy<PairingRequest>() {}
        def request = new PairingRequest('tower', 'https://tower.example.com')

        when:
        def json = encoder.encode(request)
        def decoded = encoder.decode(json)

        then:
        decoded.service == 'tower'
        decoded.endpoint == 'https://tower.example.com'
    }

    def 'should serialize and deserialize PairingResponse'() {
        given:
        def encoder = new MoshiEncodeStrategy<PairingResponse>() {}
        def response = new PairingResponse(pairingId: 'pair-123', publicKey: 'base64encodedkey')

        when:
        def json = encoder.encode(response)
        def decoded = encoder.decode(json)

        then:
        decoded.pairingId == 'pair-123'
        decoded.publicKey == 'base64encodedkey'
    }

    @Unroll
    def 'should preserve PairingRequest values - service=#service, endpoint=#endpoint'() {
        given:
        def encoder = new MoshiEncodeStrategy<PairingRequest>() {}
        def request = new PairingRequest(service, endpoint)

        when:
        def json = encoder.encode(request)
        def decoded = encoder.decode(json)

        then:
        decoded.service == service
        decoded.endpoint == endpoint

        where:
        service    | endpoint
        'tower'    | 'https://tower.example.com'
        'platform' | 'https://cloud.seqera.io'
        'custom'   | 'http://localhost:8080'
    }

    def 'should handle null fields in PairingRequest'() {
        given:
        def encoder = new MoshiEncodeStrategy<PairingRequest>() {}
        def request = new PairingRequest()

        when:
        def json = encoder.encode(request)
        def decoded = encoder.decode(json)

        then:
        decoded.service == null
        decoded.endpoint == null
    }

    def 'should handle null fields in PairingResponse'() {
        given:
        def encoder = new MoshiEncodeStrategy<PairingResponse>() {}
        def response = new PairingResponse()

        when:
        def json = encoder.encode(response)
        def decoded = encoder.decode(json)

        then:
        decoded.pairingId == null
        decoded.publicKey == null
    }
}
