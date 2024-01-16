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
        def config = new BlobCacheConfig(storageBucket: 's3://some-bucket')
        then:
        config.storageBucketName == 'some-bucket'

        when:
        config = new BlobCacheConfig(storageBucket: 's3://some-bucket/sub/path')
        then:
        config.storageBucketName == 'some-bucket'
    }
}
