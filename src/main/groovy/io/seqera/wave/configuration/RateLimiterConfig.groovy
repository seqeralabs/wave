package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
/**
 * Model Rate limiter configuration
 * 
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env = 'rate-limit')
@ConfigurationProperties('rate-limit')
@Context
@CompileStatic
interface RateLimiterConfig {

    BuildLimit getBuild()

    @ConfigurationProperties('build')
    static interface BuildLimit {
        int getMax()
        Duration getDuration()
    }

    RequestLimit getPull()

    @ConfigurationProperties('pull')
    static class RequestLimit {
        int max
        Duration duration
    }
}
