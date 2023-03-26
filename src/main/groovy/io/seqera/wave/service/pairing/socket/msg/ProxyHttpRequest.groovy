package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
/**
 * Model a remote HTTP request send via WebSocket connection
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Canonical
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class ProxyHttpRequest implements PairingMessage {
    String msgId
    String method
    String uri
    String auth
    String body
    Map<String, List<String>> headers
}
