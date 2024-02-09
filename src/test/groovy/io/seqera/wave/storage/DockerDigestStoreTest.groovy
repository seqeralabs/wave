package io.seqera.wave.storage

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerDigestStoreTest extends Specification {

    def 'should create docker store' () {
        when:
        def store = new DockerDigestStore('docker://quay.io/v2/etc','some/media','sha256:12345', 100)
        then:
        store.location == 'docker://quay.io/v2/etc'
        store.mediaType == 'some/media'
        store.digest == 'sha256:12345'
        store.size == 100

    }

}
