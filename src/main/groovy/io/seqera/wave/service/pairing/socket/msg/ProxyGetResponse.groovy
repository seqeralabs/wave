package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.ToString
import io.micronaut.http.HttpStatus

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class ProxyGetResponse implements PairingMessage {
    String msgId
    HttpStatus status
    String body
}
