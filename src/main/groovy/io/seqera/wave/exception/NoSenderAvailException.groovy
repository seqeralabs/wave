package io.seqera.wave.exception

import groovy.transform.InheritConstructors

/**
 * Exception thrown when no sender is available for a Websocket target
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@InheritConstructors
class NoSenderAvailException extends Exception {
}
