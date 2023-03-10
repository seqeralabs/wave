package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.ToString

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class ProxyGetRequest implements PairingMessage {
    String msgId
    String uri
    String bearerAuth
}
