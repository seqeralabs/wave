package io.seqera.wave.api

import java.time.Instant

import groovy.transform.CompileStatic

/**
 * Model a response for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SubmitContainerTokenResponse {
    String containerToken
    String targetImage
    Instant expiration
}
