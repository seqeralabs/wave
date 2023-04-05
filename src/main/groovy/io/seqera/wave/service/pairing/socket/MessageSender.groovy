package io.seqera.wave.service.pairing.socket

import java.util.concurrent.CompletableFuture

/**
 * Interface modelling a generic message sender
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface MessageSender<M> {

    CompletableFuture sendAsync(M message)

}
