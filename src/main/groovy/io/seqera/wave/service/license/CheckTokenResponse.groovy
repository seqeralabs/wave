package io.seqera.wave.service.license

import java.time.Instant
/**
 * Model the response for a License manager license token check request
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class CheckTokenResponse {
    String id // license ID
    Instant expiration  // license expiration timestamp
}
