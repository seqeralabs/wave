package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.ToString
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class UserResponse implements PairingPayload {
    User user
    String refreshToken
}
