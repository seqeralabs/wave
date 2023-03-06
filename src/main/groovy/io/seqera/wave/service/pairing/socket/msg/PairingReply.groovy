package io.seqera.wave.service.pairing.socket.msg


import groovy.transform.Canonical
import groovy.transform.ToString
/**
 * Model a pairing command reply
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class PairingReply<T extends PairingPayload> implements PairingMessage {

    String msgId

    T payload

}
