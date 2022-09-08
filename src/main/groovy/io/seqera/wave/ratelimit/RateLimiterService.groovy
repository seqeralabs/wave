package io.seqera.wave.ratelimit

import io.seqera.wave.exception.RateLimitException


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface RateLimiterService {

    void acquireBuild(String key) throws RateLimitException

}
