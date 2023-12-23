package io.seqera.wave.service.blob.impl

import spock.lang.Specification

import io.seqera.wave.configuration.BlobCacheConfig

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SimpleTransferStrategyTest extends Specification {

    def 'should create transfer cli' () {
        given:
        def config = new BlobCacheConfig(
                storageBucket: 's3://foo',
                storageEndpoint: 'https://foo.com',
                storageRegion: 'some-region',
                storageAccessKey: 'xyz',
                storageSecretKey: 'secret'
        )
        def strategy = new SimpleTransferStrategy(blobConfig: config)

        when:
        def result = strategy.createProcess(['s5cmd', 'run', '--this'])
        then:
        result.command() == ['s5cmd', 'run', '--this']
        result.redirectErrorStream()
        and:
        def env = result.environment()
        env.AWS_REGION == 'some-region'
        env.AWS_DEFAULT_REGION == 'some-region'
        env.AWS_ACCESS_KEY_ID == 'xyz'
        env.AWS_SECRET_ACCESS_KEY == 'secret'
    }
}
