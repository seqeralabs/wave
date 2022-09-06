package io.seqera.wave.ratelimit.impl

import io.seqera.wave.exception.RateLimitException


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface RateLimiterService {

    void acquireBuild(String key) throws RateLimitException

}
