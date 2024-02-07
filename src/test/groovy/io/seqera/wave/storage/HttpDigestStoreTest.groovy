package io.seqera.wave.storage

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class HttpDigestStoreTest extends Specification {

    def 'should create http store' () {
        when:
        def store = new HttpDigestStore('http://quay.io/v2/etc','some/media','sha256:12345', 100)
        then:
        store.location == 'http://quay.io/v2/etc'
        store.mediaType == 'some/media'
        store.digest == 'sha256:12345'
        store.size == 100

    }

}
