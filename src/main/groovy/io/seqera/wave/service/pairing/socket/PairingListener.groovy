package io.seqera.wave.service.pairing.socket


import io.seqera.wave.service.pairing.socket.msg.PairingReply
/**
 * Define the interface for receiving pairing command replies
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface PairingListener {

    void onReply(String service, String pairingId, PairingReply result)

    void close(String service, String pairingId)

}
