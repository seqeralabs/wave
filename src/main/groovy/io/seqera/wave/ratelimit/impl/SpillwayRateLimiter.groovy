/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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
import io.seqera.wave.configuration.LimitConfig
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.ratelimit.AcquireRequest
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

    Spillway<String> anonymousBuilds

    Spillway<String> authsBuilds

    Spillway<String> anonymousPulls

    Spillway<String> authsPulls

    SpillwayRateLimiter(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfig config) {
        init(storage, config)
    }

    protected void init(@NotNull LimitUsageStorage storage, @NotNull RateLimiterConfig config){
        SpillwayFactory spillwayFactory = new SpillwayFactory(storage)
        initBuilds(spillwayFactory, config)
        initPulls(spillwayFactory, config)
    }

    @Override
    void acquireBuild(AcquireRequest request) throws SlowDownException {
        Spillway<String> resource = request.userId ? authsBuilds : anonymousBuilds
        String key = request.userId ?: request.ip
        if (!resource.tryCall(key)) {
            final prefix = request.userId ? 'user' : 'IP'
            throw new SlowDownException("Request exceeded build rate limit for $prefix $key")
        }
    }

    @Override
    void acquirePull(AcquireRequest request) throws SlowDownException {
        Spillway<String> resource = request.userId ? authsPulls : anonymousPulls
        String key = request.userId ?: request.ip
        if (!resource.tryCall(key)) {
            final prefix = request.userId ? 'user' : 'IP'
            throw new SlowDownException("Request exceeded pull rate limit for $prefix $key")
        }
    }

    private void initBuilds(SpillwayFactory spillwayFactory, RateLimiterConfig config) {
        log.info "Builds anonymous rate limit: max=$config.build.anonymous.max; duration:$config.build.anonymous.duration"
        anonymousBuilds = createLimit("anonymousBuilds", spillwayFactory, config.build.anonymous)

        log.info "Builds auth rate limit: max=$config.build.authenticated.max; duration:$config.build.authenticated.duration"
        authsBuilds = createLimit("authenticatedBuilds", spillwayFactory, config.build.authenticated)
    }

    private void initPulls(SpillwayFactory spillwayFactory, RateLimiterConfig config) {
        log.info "Pulls anonymous rate limit: max=$config.pull.anonymous.max; duration:$config.pull.anonymous.duration"
        anonymousPulls = createLimit("anonymousPulls", spillwayFactory, config.pull.anonymous)

        log.info "Pulls auth rate limit: max=$config.pull.authenticated.max; duration:$config.pull.authenticated.duration"
        authsPulls = createLimit("authenticatedPulls", spillwayFactory, config.pull.authenticated)
    }

    private static Spillway<String> createLimit(String name, SpillwayFactory spillwayFactory, LimitConfig config) {
        Limit<String> userLimit = LimitBuilder.of("perUser")
                .to(config.max)
                .per(config.duration)
                .build() as Limit<String>
        spillwayFactory.enforce(name, userLimit)
    }

}
