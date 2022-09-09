package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env = 'rate-limit')
@ConfigurationProperties('rate-limit')
@Context
@CompileStatic
interface RateLimiterConfiguration {

    BuildLimit getBuild()

    @ConfigurationProperties('build')
    static interface BuildLimit {
        int getMax()

        Duration getDuration()
    }

}
