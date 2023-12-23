package io.seqera.wave.service.blob.impl

import spock.lang.Specification

import io.seqera.wave.configuration.BlobCacheConfig

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerTransferStrategyTest extends Specification {

    def 'should create transfer cli' () {
        given:
        def config = new BlobCacheConfig(
                storageBucket: 's3://foo',
                storageEndpoint: 'https://foo.com',
                storageRegion: 'some-region',
                storageAccessKey: 'xyz',
                storageSecretKey: 'secret',
                s5Image: 'cr.seqera.io/public/s5cmd:latest'
        )
        def strategy = new DockerTransferStrategy(blobConfig: config)
        and:

        when:
        def result = strategy.createProcess(['s5cmd', 'run', '--this'])
        then:
        result.command() == [
                'docker', 
                'run',
                '-e', 'AWS_ACCESS_KEY_ID',
                '-e', 'AWS_SECRET_ACCESS_KEY',
                'cr.seqera.io/public/s5cmd:latest',
                's5cmd', 'run', '--this']
        and:
        def env = result.environment()
        env.AWS_REGION == 'some-region'
        env.AWS_DEFAULT_REGION == 'some-region'
        env.AWS_ACCESS_KEY_ID == 'xyz'
        env.AWS_SECRET_ACCESS_KEY == 'secret'
        and:
        result.redirectErrorStream()
    }
}
