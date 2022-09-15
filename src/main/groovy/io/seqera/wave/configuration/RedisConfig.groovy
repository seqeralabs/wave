package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.Nullable

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable


/**
 * Model Redis server configuration settings
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('redis')
interface RedisConfig {

    String getUri()

    @Bindable(defaultValue = "1h")
    Duration getTokenExpired()
}
