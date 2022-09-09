package io.seqera.wave.ratelimit.impl


import javax.validation.constraints.NotNull

import com.coveo.spillway.Spillway
import com.coveo.spillway.SpillwayFactory
import com.coveo.spillway.limit.Limit
import com.coveo.spillway.limit.LimitBuilder
import com.coveo.spillway.storage.LimitUsageStorage
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.RateLimiterConfiguration
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

    Spillway<String> requests

    SpillwayRateLimiter(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfiguration configuration) {
        init(storage, configuration)
    }

    protected void init(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfiguration configuration) {
        SpillwayFactory spillwayFactory = new SpillwayFactory(storage)
        initBuilds(spillwayFactory, configuration)
        initRequests(spillwayFactory, configuration)
    }

    private void initBuilds(SpillwayFactory spillwayFactory, RateLimiterConfiguration configuration) {
        Limit<String> limit = LimitBuilder.of("builds")
                .to(configuration.build.max)
                .per(configuration.build.duration)
                .build();
        builds = spillwayFactory.enforce('builds', limit)
    }

    private void initRequests(SpillwayFactory spillwayFactory, RateLimiterConfiguration configuration) {
        Limit<String> limit = LimitBuilder.of("requests")
                .to(configuration.request.max)
                .per(configuration.request.duration)
                .build();
        requests = spillwayFactory.enforce('requests', limit)
    }

    @Override
    void acquireBuild(String key) throws SlowDownException{
        if( !builds.tryCall(key) )
            throw new SlowDownException("$key exceeded build rate limit")
    }

    @Override
    void acquireRequest(String key) throws SlowDownException {
        if( !requests.tryCall(key) )
            throw new SlowDownException("$key exceeded request rate limit")
    }
}
