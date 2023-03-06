package io.seqera.wave.exchange

import groovy.transform.CompileStatic
import io.seqera.wave.service.pairing.socket.msg.PairingPayload

/**
 * Model the response for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class PairingResponse implements PairingPayload {
    String pairingId
    String publicKey
}
