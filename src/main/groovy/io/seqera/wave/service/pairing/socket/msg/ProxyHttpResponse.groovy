package io.seqera.wave.service.pairing.socket.msg


import groovy.transform.Canonical
import groovy.transform.ToString

/**
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class ProxyHttpResponse implements PairingMessage {
    String msgId
    int status
    String body
    Map<String, List<String>> headers
}
