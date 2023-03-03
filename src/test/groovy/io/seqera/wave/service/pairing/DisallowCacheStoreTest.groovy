package io.seqera.wave.service.pairing

import spock.lang.Specification

import java.time.Duration
import java.time.Instant
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DisallowCacheStoreTest extends Specification {

    def 'should validate isBlocked' () {

        expect:
        !DisallowCacheStore.isBlocked0(null, Duration.ofHours(1))

        when:
        def endpoint = new DisallowCacheStore.Endpoint('https://foo.com', Instant.now())
        then:
        !DisallowCacheStore.isBlocked0(endpoint, Duration.ofHours(1))

        when:
        endpoint = new DisallowCacheStore.Endpoint('https://foo.com', Instant.now().minus(Duration.ofHours(2)))
        then:
        DisallowCacheStore.isBlocked0(endpoint, Duration.ofHours(1))
    }

}
