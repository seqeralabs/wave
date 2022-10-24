package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable

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

        @Bindable("10 / 1h")
        LimitConfig getAnonymous()

        @Bindable("10 / 1m")
        LimitConfig getAuthenticated()
    }

    RequestLimit getPull()

    @ConfigurationProperties('pull')
    static interface RequestLimit {

        @Bindable("100 / 1h")
        LimitConfig getAnonymous()

        @Bindable("100 / 1m")
        LimitConfig getAuthenticated()
    }

}
