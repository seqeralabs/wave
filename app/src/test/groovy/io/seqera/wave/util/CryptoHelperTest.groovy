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

package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CryptoHelperTest extends Specification {

    def 'should validate hmac token' () {
        given:
        def message = 'Hello world'
        def secret = 'blah blah'

        expect:
        CryptoHelper.computeHmacSha256(message, secret) == CryptoHelper.computeHmacSha256(message, secret)
    }

    @Unroll
    def 'should compute cloudflare token' () {

        expect:
        CryptoHelper.computeCloudflareWafToken(URI.create(URI_PATH), TIME, SECRET) == EXPECTED

        where:
        URI_PATH                        | TIME          | SECRET    | EXPECTED
        'https://foo.com/hello.txt'     | 1705931639    | 'foo'     | '1705931639-QdwQe6e5lNYUJhvHPomrOVouQcrti4m7AYHqbyOR6Iw%3D'
        'https://foo.com/hello.txt'     | 1705931639    | 'bar'     | '1705931639-UjaTxEs3jhIgWp3LHAoKTpd3ZFXyy%2F7kr%2BYlL1m%2BTCE%3D'
        'https://bar.com/hello.txt'     | 1705931639    | 'foo'     | '1705931639-QdwQe6e5lNYUJhvHPomrOVouQcrti4m7AYHqbyOR6Iw%3D'
        'https://foo.com/hello.txt'     | 1705931500    | 'foo'     | '1705931500-%2B2iV5xOHG0OEt9fGsaOYhXMfpht6PeKM3j4Q5u%2FConw%3D'
        and:
        'https://foo.com/images/cat.jpg'| 1484063787    | 'mysecrettoken'   | '1484063787-XsZ5NmtNBxOd4f4fBOtqVmpWRnDRNkHKVZieqDPKu3U%3D'

    }
}
