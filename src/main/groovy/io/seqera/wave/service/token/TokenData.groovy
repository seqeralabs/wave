package io.seqera.wave.service.token

import java.time.Instant

import groovy.transform.Canonical

/**
 * Model container token
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class TokenData {
    final String value
    final Instant expiration
}
