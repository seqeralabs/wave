package io.seqera.wave.service.pairing.socket.msg


import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model a remote HTTP response send via WebSocket connection
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Canonical
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class ProxyHttpResponse implements PairingMessage {
    String msgId
    int status
    String body
    Map<String, List<String>> headers
}
