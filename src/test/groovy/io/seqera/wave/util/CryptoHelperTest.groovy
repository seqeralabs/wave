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
        'https://foo.com/hello.txt'     | 1705931639    | 'foo'     | 'QdwQe6e5lNYUJhvHPomrOVouQcrti4m7AYHqbyOR6Iw='
        'https://foo.com/hello.txt'     | 1705931639    | 'bar'     | 'UjaTxEs3jhIgWp3LHAoKTpd3ZFXyy/7kr+YlL1m+TCE='
        'https://bar.com/hello.txt'     | 1705931639    | 'foo'     | 'QdwQe6e5lNYUJhvHPomrOVouQcrti4m7AYHqbyOR6Iw='
        'https://foo.com/hello.txt'     | 1705931500    | 'foo'     | '+2iV5xOHG0OEt9fGsaOYhXMfpht6PeKM3j4Q5u/Conw='
    }
}
