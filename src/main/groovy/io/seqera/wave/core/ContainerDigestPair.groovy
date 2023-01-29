package io.seqera.wave.core

import groovy.transform.Canonical

/**
 * Hold the container digest that originated the request and the
 * digest of the resolved container provisioned by wave
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class ContainerDigestPair {
    /**
     * Digest of the source container image
     */
    final String source

    /**
     * Digest of the augmented container image
     */
    final String resolved
}
