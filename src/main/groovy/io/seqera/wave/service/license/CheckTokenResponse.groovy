package io.seqera.wave.service.license

import java.time.Instant
/**
 * CheckTokenResponse model
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class CheckTokenResponse {
    String id // license ID
    Instant expiration  // license expiration timestamp
}
