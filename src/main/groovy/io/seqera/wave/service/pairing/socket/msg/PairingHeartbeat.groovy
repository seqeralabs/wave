package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model pairing heartbeat message
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class PairingHeartbeat implements PairingMessage {
    String msgId
}
