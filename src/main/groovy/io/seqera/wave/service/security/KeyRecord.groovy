package io.seqera.wave.service.security

import groovy.transform.Canonical
import groovy.transform.ToString

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(excludes = 'privateKey')
class KeyRecord {
    String service
    String instanceId
    String hostname
    String keyId
    byte[] privateKey
}
