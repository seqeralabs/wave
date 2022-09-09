package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.Nullable

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Value
import io.micronaut.core.bind.annotation.Bindable


/**
 * Configuration to be used by a TokenService
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave.tokens')
interface TokenConfiguration {

    Cache getCache()

    @ConfigurationProperties('cache')
    interface Cache {

        @Bindable(defaultValue = "1h")
        @Nullable
        Duration getDuration()

        @Bindable(defaultValue = "10000")
        @Nullable
        int getMaxSize()
    }
}
