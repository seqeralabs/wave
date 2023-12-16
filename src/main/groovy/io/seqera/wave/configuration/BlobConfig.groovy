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

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    @Value('${wave.blob.status.duration:5d}')
    Duration statusDuration

}
