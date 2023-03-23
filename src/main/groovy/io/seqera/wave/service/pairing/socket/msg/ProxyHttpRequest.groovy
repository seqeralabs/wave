package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
/**
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
    String bearerAuth
    String body
    Map<String, List<String>> headers
}
