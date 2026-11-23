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

package io.seqera.wave.store.crypto

import spock.lang.Specification

import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class StoreEncryptionTest extends Specification {

    static StoreEncryption encryption(List<String> keys) {
        final result = new StoreEncryption(keys: keys)
        result.init()
        return result
    }

    def 'should round trip a value and not leave the plaintext in it' () {
        given:
        def crypto = encryption(['a-very-secret-key'])

        when:
        def sealed = crypto.encrypt('the-access-token')

        then:
        sealed.startsWith('enc:')
        !sealed.contains('the-access-token')
        and:
        crypto.decrypt(sealed) == 'the-access-token'
    }

    def 'should produce a different ciphertext each time for the same input' () {
        given:
        def crypto = encryption(['a-very-secret-key'])

        expect: 'a random IV, so no two encryptions match'
        crypto.encrypt('the-access-token') != crypto.encrypt('the-access-token')
    }

    def 'should pass values through when no key is configured' () {
        given:
        def crypto = encryption(KEYS)

        expect:
        !crypto.enabled
        crypto.encrypt('the-access-token') == 'the-access-token'
        crypto.decrypt('the-access-token') == 'the-access-token'

        where:
        KEYS << [null, [], ['  ']]
    }

    def 'should leave null alone' () {
        given:
        def crypto = encryption(['a-very-secret-key'])

        expect:
        crypto.encrypt(null) == null
        crypto.decrypt(null) == null
    }

    def 'should read back a record written before encryption was switched on' () {
        given:
        def crypto = encryption(['a-very-secret-key'])

        expect: 'a value with no enc: prefix is returned untouched'
        crypto.decrypt('eyJhbGciOiJIUzI1NiJ9.cleartext-legacy-token') == 'eyJhbGciOiJIUzI1NiJ9.cleartext-legacy-token'
    }

    def 'should decrypt with a rotated-out key and write with the newest' () {
        given:
        def oldKey = encryption(['key-one'])
        def sealed = oldKey.encrypt('the-access-token')
        and: 'key-two is rolled in front of key-one'
        def rotated = encryption(['key-two', 'key-one'])

        expect: 'the record written under the old key still opens'
        rotated.decrypt(sealed) == 'the-access-token'
        and: 'new records are written under the new key, not the old one'
        def newPrefix = rotated.encrypt('x').split(':')[1]
        def oldPrefix = oldKey.encrypt('x').split(':')[1]
        newPrefix != oldPrefix
        and: 'the key id is not a hash of the configured passphrase'
        !newPrefix.contains(io.seqera.wave.util.DigestFunctions.md5('key-two').substring(0, 8))
    }

    def 'should fail clearly when the key that sealed a record is gone' () {
        given:
        def sealed = encryption(['key-one']).encrypt('the-access-token')

        when:
        encryption(['key-two']).decrypt(sealed)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('no encryption key with id')
    }

    def 'should keep the access token of a PlatformId out of the serialized record' () {
        given:
        def crypto = encryption(['a-very-secret-key'])
        def serde = new MoshiEncodeStrategy<PlatformId>(crypto.factory()) {}
        def id = new PlatformId(new User(id: 1, email: 'me@seqera.io'), 100L, 'the-access-token', 'http://tower.io', 'wf-1')

        when:
        def json = serde.encode(id)

        then: 'the token is not readable in what the store would write'
        !json.contains('the-access-token')
        json.contains('enc:')
        and: 'the non-credential fields stay as they were'
        json.contains('me@seqera.io')
        json.contains('http://tower.io')
        and: 'and it round trips'
        serde.decode(json) == id
    }

    def 'should keep both JwtAuth tokens out of the serialized record' () {
        given:
        def crypto = encryption(['a-very-secret-key'])
        def serde = new MoshiEncodeStrategy<JwtAuth>(crypto.factory()) {}
        def auth = JwtAuth.of('http://tower.io', 'the-access-token', 'the-refresh-token')

        when:
        def json = serde.encode(auth)

        then:
        !json.contains('the-access-token')
        !json.contains('the-refresh-token')
        and:
        serde.decode(json) == auth
    }

    def 'should serialize a null refresh token without a prefix' () {
        given:
        def crypto = encryption(['a-very-secret-key'])
        def serde = new MoshiEncodeStrategy<JwtAuth>(crypto.factory()) {}
        def auth = JwtAuth.of('http://tower.io', 'the-access-token')

        expect:
        serde.decode(serde.encode(auth)) == auth
    }

}
