package io.seqera.wave.ratelimit.impl

import java.time.Duration
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.bind.annotation.Bindable


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('rate-limit')
@Context
@CompileStatic
class RateLimiterConfiguration {

    BuildLimit build

    @ConfigurationProperties('build')
    static class BuildLimit {
        int max

        Duration duration
    }

    Spillway spillway

    @ConfigurationProperties('spillway')
    static interface Spillway{

        /*
        * Implementation to use. Valid values: memory or redis
         */
        String getImpl()

        @Bindable(defaultValue =  "localhost")
        @Nullable
        String getRedisHost()

        @Bindable(defaultValue =  "6379")
        @Nullable
        int getRedisPort()
    }
}
