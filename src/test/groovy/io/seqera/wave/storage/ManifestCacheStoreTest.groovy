package io.seqera.wave.storage

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ManifestCacheStoreTest extends Specification {

    def 'should strip registry' () {
        expect:
        ManifestCacheStore.stripRegistry(PATH) == EXPECTED

        where:
        PATH                | EXPECTED
        null                | null
        'foo'               | null
        '/'                 | null
        '/foo/bar'          | null
        'docker.io'         | null
        and:
        'docker.io/'        | '/'
        'docker.io/foo/bar' | '/foo/bar'
    }

}
