package io.seqera.wave.ratelimit.impl


import javax.validation.constraints.NotNull

import com.coveo.spillway.Spillway
import com.coveo.spillway.SpillwayFactory
import com.coveo.spillway.limit.Limit
import com.coveo.spillway.limit.LimitBuilder
import com.coveo.spillway.storage.LimitUsageStorage
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.ratelimit.RateLimiterService
import jakarta.inject.Singleton


/**
 * This class manage how many requests can be requested from an user during a configurable period
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env = 'rate-limit')
@Singleton
@CompileStatic
class SpillwayRateLimiter implements RateLimiterService{

    Spillway<String> builds

    SpillwayRateLimiter(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfig configuration) {
        init(storage, configuration)
    }

    protected void init(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfig configuration){
        SpillwayFactory spillwayFactory = new SpillwayFactory(storage)
        Limit<String> limitBuilds = LimitBuilder.of("builds")
                .to(configuration.build.max)
                .per(configuration.build.duration)
                .build();
        builds = spillwayFactory.enforce('builds', limitBuilds)
    }

    @Override
    void acquireBuild(String key) throws SlowDownException{
        if( !builds.tryCall(key) )
            throw new SlowDownException("$key exceeded rate limit")
    }

}
