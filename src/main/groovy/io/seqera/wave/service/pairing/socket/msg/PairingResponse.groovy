package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.CompileStatic

/**
 * Model the response for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class PairingResponse implements PairingMessage {
    String msgId
    String pairingId
    String publicKey
}
