package io.seqera.wave.configuration

import java.time.Duration

import io.micronaut.context.annotation.Value

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobConfig {

    @Value('${wave.blob.status.delay:5s}')
    Duration statusDelay

    @Value('${wave.blob.timeout:5m}')
    Duration transferTimeout

    @Value('${wave.blob.status.duration:5d}')
    Duration statusDuration

    @Value('${wave.blob.bucket:`s3://nextflow-ci/blobs`}')
    String bucket

    @Value('${wave.blob.baseUrl:`https://nextflow-ci.s3.eu-west-1.amazonaws.com/blobs`}')
    String baseUrl
}
