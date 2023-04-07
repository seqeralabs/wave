package io.seqera.wave.service.pairing.socket
/**
 * Interface modelling a generic message sender
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface MessageSender<M> {

    void send(M message)

}
