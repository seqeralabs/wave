package io.seqera.wave.ratelimit.impl


import javax.validation.constraints.NotNull

import com.coveo.spillway.Spillway
import com.coveo.spillway.SpillwayFactory
import com.coveo.spillway.limit.Limit
import com.coveo.spillway.limit.LimitBuilder
import com.coveo.spillway.storage.LimitUsageStorage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
@Slf4j
@Requires(env = 'rate-limit')
@Singleton
@CompileStatic
class SpillwayRateLimiter implements RateLimiterService {

    Spillway<String> builds

    Spillway<String> pulls

    SpillwayRateLimiter(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfig config) {
        init(storage, config)
    }

    protected void init(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfig config){
        SpillwayFactory spillwayFactory = new SpillwayFactory(storage)
        initBuilds(spillwayFactory, config)
        initPulls(spillwayFactory, config)
    }

    private void initBuilds(SpillwayFactory spillwayFactory, RateLimiterConfig config) {
        Limit<String> limit = LimitBuilder.of("builds")
                .to(config.build.max)
                .per(config.build.duration)
                .build();
        builds = spillwayFactory.enforce('builds', limit)
        log.info "Builds rate limit: max=$config.build.max; duration:$config.build.duration"
    }

    private void initPulls(SpillwayFactory spillwayFactory, RateLimiterConfig config) {
        Limit<String> limit = LimitBuilder.of("pulls")
                .to(config.pull.max)
                .per(config.pull.duration)
                .build();
        pulls = spillwayFactory.enforce('pulls', limit)
        log.info "Pulls rate limit: max=$config.pull.max; duration:$config.pull.duration"
    }

    @Override
    void acquireBuild(String key) throws SlowDownException {
        if( !builds.tryCall(key) )
            throw new SlowDownException("$key exceeded build rate limit")
    }

    @Override
    void acquirePull(String key) throws SlowDownException {
        if( !pulls.tryCall(key) )
            throw new SlowDownException("$key exceeded pull rate limit")
    }
}
