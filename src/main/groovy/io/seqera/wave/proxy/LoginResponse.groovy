package io.seqera.wave.proxy

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
class LoginResponse {
    String token
    String access_token
    Integer expires_in
    String issued_at
}
