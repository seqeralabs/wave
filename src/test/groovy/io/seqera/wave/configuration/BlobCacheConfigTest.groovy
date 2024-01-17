package io.seqera.wave.configuration

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheConfigTest extends Specification {

    def 'should get config env' () {
        when:
        def config = new BlobCacheConfig()
        then:
        config.getEnvironment() == [:]

        when:
        config = new BlobCacheConfig(storageAccessKey: 'xyz', storageSecretKey: 'secret', storageRegion: 'foo')
        then:
        config.getEnvironment() == [
                AWS_REGION: 'foo',
                AWS_DEFAULT_REGION: 'foo',
                AWS_ACCESS_KEY_ID: 'xyz',
                AWS_SECRET_ACCESS_KEY: 'secret'
        ]
    }

    def 'should get bucket name' (){
        when:
        def config = new BlobCacheConfig(storageBucket: BUCKET)
        then:
        config.storageBucket == EXPECTED

        where:
        BUCKET                  | EXPECTED
        null                    | null
        'foo'                   | 's3://foo'
        's3://foo'              | 's3://foo'
        's3://foo/bar'          | 's3://foo/bar'
    }
}
