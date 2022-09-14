package io.seqera.wave.ratelimit

import io.seqera.wave.exception.SlowDownException


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface RateLimiterService {

    void acquireBuild(AcquireRequest request) throws SlowDownException

    void acquirePull(AcquireRequest request) throws SlowDownException
}
