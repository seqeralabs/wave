package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model the response for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class PairingResponse implements PairingMessage {
    String msgId
    String pairingId
    String publicKey
}
