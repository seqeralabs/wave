package io.seqera.wave.service.security

import groovy.transform.Canonical
import groovy.transform.ToString

/**
 * Model a security key record associated with a registered service endpoint
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(excludes = 'privateKey')
class KeyRecord {
    String service
    String hostname
    String keyId
    byte[] privateKey
    byte[] publicKey
}
