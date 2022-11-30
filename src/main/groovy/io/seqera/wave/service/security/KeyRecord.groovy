package io.seqera.wave.service.security

import groovy.transform.Canonical

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class KeyRecord {
    String service
    String instanceId
    String hostname
    byte[] privateKey
}
