package io.seqera.wave.ratelimit

import io.seqera.wave.exception.SlowDownException


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface RateLimiterService {

    void acquireBuild(String key) throws SlowDownException

    void acquirePull(String key) throws SlowDownException
}
